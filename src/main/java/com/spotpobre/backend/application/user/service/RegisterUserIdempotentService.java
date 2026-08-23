package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.idempotency.Claim;
import com.spotpobre.backend.application.idempotency.ClaimOutcome;
import com.spotpobre.backend.application.idempotency.IdempotencyCoordinator;
import com.spotpobre.backend.application.user.port.in.RegisterUserIdempotentlyUseCase;
import com.spotpobre.backend.application.user.port.in.RegisterUserUseCase.RegisterUserCommand;
import com.spotpobre.backend.domain.common.ConflictException;
import com.spotpobre.backend.domain.common.IdempotencyConflictException;
import com.spotpobre.backend.domain.common.IdempotencyInProgressException;
import com.spotpobre.backend.domain.common.IdempotencyKey;
import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.FailureDescriptor;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyScope;
import com.spotpobre.backend.domain.idempotency.model.ResultSnapshot;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.AccountTokenRepository;
import com.spotpobre.backend.domain.user.port.EmailSenderPort;
import com.spotpobre.backend.domain.user.port.PasswordHasher;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Idempotent registration (spec §5.4–§5.7, §4.3): anonymous scope, canonical request hash over
 * the validated command fields, stable preassigned {@code UserId}, conditional unique-email
 * insert and crash recovery by loading the reserved user. Only the safe snapshot is stored —
 * JWTs are minted fresh by the web layer on every execution and never persisted.
 *
 * <p>Failure policy after a successful claim: deterministic conflicts (email already exists)
 * are marked FAILED_FINAL and replayed; unexpected failures retain the IN_PROGRESS record so a
 * retry recovers through lease takeover instead of duplicating the user.</p>
 */
@RequiredArgsConstructor
public class RegisterUserIdempotentService implements RegisterUserIdempotentlyUseCase {

    static final String API_VERSION = "v1";
    static final String ROUTE_TEMPLATE = "/api/v1/auth/register";
    static final Duration RETRY_AFTER_CAP = Duration.ofSeconds(30);

    private final IdempotencyCoordinator coordinator;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;
    private final AccountTokenRepository accountTokenRepository;
    private final EmailSenderPort emailSenderPort;
    private final com.spotpobre.backend.infrastructure.config.properties.EmailProperties emailProperties;

    @Override
    @Transactional
    public RegistrationOutcome registerIdempotently(final String rawIdempotencyKey,
                                                    final RegisterUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Registration command cannot be null.");
        }
        // Domain validation of the raw header value; IllegalArgumentException maps to 400.
        final IdempotencyKey key = IdempotencyKey.of(rawIdempotencyKey);

        final IdempotencyScope scope = IdempotencyScope.anonymousRegistration(
                API_VERSION, "POST", ROUTE_TEMPLATE, key);
        final CanonicalRequestHash requestHash = CanonicalRequestHash.current(List.of(
                command.name(), command.email(), command.password(), command.country()));

        final ClaimOutcome outcome = coordinator.claim(scope, requestHash, "RegisterUser",
                IdempotencyResourceType.USER, IdempotencyCoordinator.DEFAULT_CREATION_LEASE);

        if (outcome.replay().isPresent()) {
            return new RegistrationOutcome(loadReservedUser(outcome.replay().get().resourceId()), true);
        }
        if (outcome.replayedFailure().isPresent()) {
            throw failureToException(outcome.replayedFailure().get().failure());
        }
        if (outcome.isActiveLeaseElsewhere()) {
            long retryAfter = Math.min(RETRY_AFTER_CAP.getSeconds(),
                    coordinator.retryAfterSecondsFor(outcome.activeLease().orElse(null),
                            Duration.ofSeconds(2)));
            throw new IdempotencyInProgressException(
                    "A registration with this Idempotency-Key is already in progress.", retryAfter);
        }
        if (outcome.isKeyReusedWithDifferentRequest()) {
            throw new IdempotencyConflictException(
                    "This Idempotency-Key was already used with a different request.");
        }

        final Claim claim = outcome.claimed().orElseThrow();
        try {
            final User user = executeRegistration(claim.resourceId(), command);
            coordinator.completeClaim(claim,
                    ResultSnapshot.jsonBody("{\"userId\":\"" + claim.resourceId() + "\"}"),
                    clock.instant());
            // Binding decision v0.12.0: first successful attempt (fresh insert or crash
            // recovery) sends the verification e-mail at-least-once; idempotent REPLAYS
            // never resend. Delivery failures are logged, never surfaced to the caller.
            sendVerificationEmailBestEffort(user);
            return new RegistrationOutcome(user, false);
        } catch (ConflictException e) {
            coordinator.failClaim(claim,
                    FailureDescriptor.of(409, "EMAIL_ALREADY_EXISTS",
                            "User with this email already exists."),
                    clock.instant());
            throw e;
        }
        // Any other exception intentionally leaves the record IN_PROGRESS: the write may have
        // landed, so a retry must recover via takeover instead of re-inserting.
    }

    /**
     * Executes the business write under the reserved resource ID. Crash recovery: when the
     * reserved user already exists (previous attempt crashed after the conditional insert), the
     * existing user is returned instead of inserting again.
     */
    private User executeRegistration(final String reservedResourceId, final RegisterUserCommand command) {
        final UserId reservedId = UserId.from(reservedResourceId);
        final Optional<User> recovered = userRepository.findById(reservedId);
        if (recovered.isPresent()) {
            return recovered.get();
        }

        final UserProfile profile = new UserProfile(command.name(), command.email(), command.country());
        final String hashedPassword = passwordHasher.encode(command.password());
        final User newUser = User.createWithLocalPassword(reservedId, profile, hashedPassword);

        if (!userRepository.createIfEmailNotExists(newUser)) {
            throw new ConflictException("User with email " + command.email() + " already exists.");
        }
        return newUser;
    }

    private User loadReservedUser(final String userId) {
        return userRepository.findById(UserId.from(userId))
                .orElseThrow(() -> new IdempotencyConflictException(
                        "The registered account for this Idempotency-Key no longer exists."));
    }

    /**
     * Best-effort delivery: registration must succeed even when the e-mail provider is
     * unreachable. The token is persisted BEFORE the send attempt, so a crash after the save but
     * before delivery leaves a valid (unused) token with no e-mail in flight — recovery is the
     * authenticated resend endpoint.
     */
    private void sendVerificationEmailBestEffort(final User user) {
        try {
            final String rawToken = newRawToken();
            accountTokenRepository.save(com.spotpobre.backend.domain.user.model.AccountToken.issue(
                    user.getId(), com.spotpobre.backend.domain.user.model.AccountTokenPurpose.EMAIL_VERIFICATION,
                    rawToken, emailProperties.verificationTtl(), clock.instant()));
            emailSenderPort.sendEmailVerificationEmail(user.getProfile().email(), rawToken);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(RegisterUserIdempotentService.class)
                    .error("Failed to deliver verification e-mail to {}", user.getProfile().email(), e);
        }
    }

    private static String newRawToken() {
        final byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private RuntimeException failureToException(final FailureDescriptor failure) {
        if (failure.status() == 409) {
            return new ConflictException(failure.message());
        }
        return new IllegalArgumentException(failure.message());
    }
}
