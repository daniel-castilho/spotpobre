package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.idempotency.ClaimOutcome;
import com.spotpobre.backend.application.idempotency.IdempotencyCoordinator;
import com.spotpobre.backend.application.idempotency.InMemoryIdempotencyRecordRepository;
import com.spotpobre.backend.application.idempotency.port.out.IdempotencyMetrics;
import com.spotpobre.backend.application.user.port.in.RegisterUserIdempotentlyUseCase.RegistrationOutcome;
import com.spotpobre.backend.application.user.port.in.RegisterUserUseCase.RegisterUserCommand;
import com.spotpobre.backend.domain.common.ConflictException;
import com.spotpobre.backend.domain.common.IdempotencyConflictException;
import com.spotpobre.backend.domain.common.IdempotencyInProgressException;
import com.spotpobre.backend.domain.common.IdempotencyKey;
import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyScope;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyState;
import com.spotpobre.backend.domain.idempotency.model.LeaseToken;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.port.PasswordHasher;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterUserIdempotentServiceTest {

    private static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");
    private static final RegisterUserCommand COMMAND =
            new RegisterUserCommand("Ada Lovelace", "ada@example.com", "sup3rSecret!", "BR");

    private MutableClock clock;
    private InMemoryIdempotencyRecordRepository idempotencyStore;
    private IdempotencyCoordinator coordinator;
    private UserRepository userRepository;
    private PasswordHasher passwordHasher;
    private RegisterUserIdempotentService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        idempotencyStore = new InMemoryIdempotencyRecordRepository();
        coordinator = new IdempotencyCoordinator(idempotencyStore, clock, NoopMetrics.INSTANCE);
        userRepository = mock(UserRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        service = new RegisterUserIdempotentService(coordinator, userRepository, passwordHasher, clock);
    }

    @Test
    void registerIdempotently_newKey_createsUserUnderReservedIdAndCompletes() {
        when(passwordHasher.encode(COMMAND.password())).thenReturn("$argon2-hash$");
        when(userRepository.createIfEmailNotExists(any(User.class))).thenReturn(true);
        String key = validKey();

        RegistrationOutcome outcome = service.registerIdempotently(key, COMMAND);

        assertFalse(outcome.replayed());
        assertTrue(outcome.user().getId().value() != null);
        verify(userRepository).createIfEmailNotExists(any(User.class));

        // The completed record stores only the safe snapshot reference, never a JWT.
        var stored = idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow();
        assertEquals(IdempotencyState.COMPLETED, stored.state());
        assertEquals(outcome.user().getId().value().toString(), stored.resourceId(),
                "the persisted user must carry the id reserved by the claim");
        assertEquals("{\"userId\":\"" + outcome.user().getId().value() + "\"}", stored.resultSnapshot().body());
    }

    @Test
    void registerIdempotently_sameKeySameRequest_replaysSameUserWithoutSecondInsert() {
        String key = validKey();
        // Faithful storage simulation: the user becomes findable only after the insert lands,
        // and it carries exactly the id the claim reserved.
        java.util.concurrent.atomic.AtomicReference<User> saved = new java.util.concurrent.atomic.AtomicReference<>();
        when(userRepository.findById(any(UserId.class))).thenAnswer(inv -> {
            UserId asked = inv.getArgument(0);
            User storedUser = saved.get();
            return storedUser != null && storedUser.getId().equals(asked)
                    ? Optional.of(storedUser) : Optional.empty();
        });
        when(userRepository.createIfEmailNotExists(any(User.class))).thenAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return true;
        });
        when(passwordHasher.encode(COMMAND.password())).thenReturn("$argon2-hash$");

        RegistrationOutcome first = service.registerIdempotently(key, COMMAND);
        RegistrationOutcome second = service.registerIdempotently(key, COMMAND);

        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertEquals(first.user().getId(), second.user().getId());
        verify(userRepository, times(1)).createIfEmailNotExists(any(User.class));
    }

    @Test
    void registerIdempotently_missingBlankOrInvalidKey_mapsToIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.registerIdempotently(null, COMMAND));
        assertThrows(IllegalArgumentException.class, () -> service.registerIdempotently("   ", COMMAND));
        assertThrows(IllegalArgumentException.class, () -> service.registerIdempotently("short-key", COMMAND));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerIdempotently("has spaces inside-123456", COMMAND));
        verify(userRepository, never()).createIfEmailNotExists(any());
    }

    @Test
    void registerIdempotently_sameKeyDifferentRequest_returnsKeyReuseConflict() {
        String key = validKey();
        when(passwordHasher.encode(COMMAND.password())).thenReturn("$argon2-hash$");
        when(userRepository.createIfEmailNotExists(any(User.class))).thenReturn(true);
        service.registerIdempotently(key, COMMAND);

        RegisterUserCommand other = new RegisterUserCommand(
                COMMAND.name(), COMMAND.email(), "different-password-1", COMMAND.country());

        assertThrows(IdempotencyConflictException.class, () -> service.registerIdempotently(key, other));
    }

    @Test
    void registerIdempotently_emailConflict_marksFailedFinalAndReplaysFailureOnRetry() {
        when(passwordHasher.encode(COMMAND.password())).thenReturn("$argon2-hash$");
        when(userRepository.createIfEmailNotExists(any(User.class))).thenReturn(false);

        String key = validKey();
        assertThrows(ConflictException.class, () -> service.registerIdempotently(key, COMMAND));

        // Retry with the same key replays the deterministic failure without touching storage again.
        assertThrows(ConflictException.class, () -> service.registerIdempotently(key, COMMAND));
        verify(userRepository, times(1)).createIfEmailNotExists(any(User.class));
    }

    @Test
    void registerIdempotently_activeForeignLease_throwsInProgressWithCappedRetryAfter() {
        // A foreign execution holds a live lease for this exact scope/request.
        String key = validKey();
        coordinator.claim(scopeOf(key), requestHash(), "RegisterUser",
                com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType.USER,
                IdempotencyCoordinator.DEFAULT_CREATION_LEASE);

        IdempotencyInProgressException exception = assertThrows(IdempotencyInProgressException.class,
                () -> service.registerIdempotently(key, COMMAND));

        long retryAfter = exception.getRetryAfterSeconds();
        assertTrue(retryAfter >= 1 && retryAfter <= 30, "retry-after must be capped to a small positive integer");
    }

    @Test
    void registerIdempotently_crashBeforeWrite_takesOverLeaseAndInsertsOnce() {
        String key = validKey();
        IdempotencyScope scope = scopeOf(key);
        ClaimOutcome crashed = coordinator.claim(scope, requestHash(), "RegisterUser",
                com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType.USER,
                IdempotencyCoordinator.DEFAULT_CREATION_LEASE);
        // Simulated crash: claimed, nothing written, lease left to expire.
        clock.advance(IdempotencyCoordinator.DEFAULT_CREATION_LEASE.multipliedBy(2));

        UserId reservedId = UserId.from(crashed.claimed().orElseThrow().resourceId());
        when(userRepository.findById(reservedId)).thenReturn(Optional.empty());
        when(passwordHasher.encode(COMMAND.password())).thenReturn("$argon2-hash$");
        when(userRepository.createIfEmailNotExists(any(User.class))).thenReturn(true);

        RegistrationOutcome outcome = service.registerIdempotently(key, COMMAND);

        assertFalse(outcome.replayed());
        assertEquals(reservedId, outcome.user().getId(),
                "takeover must reuse the reserved resource ID instead of generating a new one");
        verify(userRepository, times(1)).createIfEmailNotExists(same(outcome.user()));
    }

    @Test
    void registerIdempotently_crashAfterWrite_recoversExistingUserWithoutReinserting() {
        String key = validKey();
        IdempotencyScope scope = scopeOf(key);
        ClaimOutcome crashed = coordinator.claim(scope, requestHash(), "RegisterUser",
                com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType.USER,
                IdempotencyCoordinator.DEFAULT_CREATION_LEASE);
        clock.advance(IdempotencyCoordinator.DEFAULT_CREATION_LEASE.multipliedBy(2));

        UserId reservedId = UserId.from(crashed.claimed().orElseThrow().resourceId());
        User writtenBeforeCrash = userWith(reservedId);
        when(userRepository.findById(reservedId)).thenReturn(Optional.of(writtenBeforeCrash));

        RegistrationOutcome outcome = service.registerIdempotently(key, COMMAND);

        assertFalse(outcome.replayed(), "recovery executes the operation once more to completion");
        assertSame(writtenBeforeCrash, outcome.user());
        verify(userRepository, never()).createIfEmailNotExists(any());
        assertEquals(IdempotencyState.COMPLETED,
                idempotencyStore.findByScopeKey(scope.scopeKey()).orElseThrow().state());
    }

    @Test
    void registerIdempotently_unexpectedFailureAfterClaim_retainsInProgressForRecovery() {
        String key = validKey();
        when(userRepository.findById(any())).thenThrow(new IllegalStateException("DynamoDB down"));

        assertThrows(IllegalStateException.class, () -> service.registerIdempotently(key, COMMAND));

        var stored = idempotencyStore.findByScopeKey(scopeOf(key).scopeKey()).orElseThrow();
        assertEquals(IdempotencyState.IN_PROGRESS, stored.state(),
                "unknown failures retain the record so retries recover through takeover");
    }

    @Test
    void registerIdempotently_differentKeysSameEmail_secondFailsWithNormalBusinessConflict() {
        when(passwordHasher.encode(COMMAND.password())).thenReturn("$argon2-hash$");
        when(userRepository.createIfEmailNotExists(any(User.class)))
                .thenReturn(true)
                .thenReturn(false);

        service.registerIdempotently(validKey(), COMMAND);
        assertThrows(ConflictException.class, () -> service.registerIdempotently(validKey(), COMMAND),
                "different keys must retain normal unique-email semantics");
    }

    private static String validKey() {
        return "reg-it-" + UUID.randomUUID();
    }

    private static IdempotencyScope scopeOf(final String rawKey) {
        return IdempotencyScope.anonymousRegistration(RegisterUserIdempotentService.API_VERSION,
                "POST", RegisterUserIdempotentService.ROUTE_TEMPLATE, IdempotencyKey.of(rawKey));
    }

    private static CanonicalRequestHash requestHash() {
        return CanonicalRequestHash.current(List.of(
                COMMAND.name(), COMMAND.email(), COMMAND.password(), COMMAND.country()));
    }

    private static User userWith(final UserId userId) {
        return User.createWithLocalPassword(userId,
                new com.spotpobre.backend.domain.user.model.UserProfile(
                        COMMAND.name(), COMMAND.email(), COMMAND.country()),
                "$argon2-hash$");
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(final Instant start) {
            this.instant = start;
        }

        void advance(final Duration d) {
            instant = instant.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private enum NoopMetrics implements IdempotencyMetrics {
        INSTANCE;

        @Override
        public void incrementClaimOutcome(final ClaimOutcomeTag outcome) {
        }

        @Override
        public void incrementTransition(final TransitionTag transition) {
        }
    }
}
