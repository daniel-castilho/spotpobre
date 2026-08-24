package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.EmailVerificationSettings;
import com.spotpobre.backend.application.user.port.in.RequestEmailVerificationResendUseCase;
import com.spotpobre.backend.domain.common.TooManyRequestsException;
import com.spotpobre.backend.domain.user.model.AccountToken;
import com.spotpobre.backend.domain.user.model.AccountTokenPurpose;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.port.AccountTokenRepository;
import com.spotpobre.backend.domain.user.port.EmailSenderPort;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
@Service
public class RequestEmailVerificationResendService implements RequestEmailVerificationResendUseCase {

    /** Minimum interval between verification e-mails for the same account (per instance). */
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final UserRepository userRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final EmailSenderPort emailSenderPort;
    private final Clock clock;
    private final EmailVerificationSettings emailSettings;

    private final SecureRandom secureRandom = new SecureRandom();
    // In-memory per-user cooldown: same consistency class as FixedWindowRateLimiter (drift
    // between replicas only widens the effective window; it can never leak extra e-mails).
    private final Map<UserId, Instant> lastSentAt = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public void requestResend(final RequestEmailVerificationResendCommand command) {
        final UserId userId = command.userId();
        enforceCooldown(userId);

        final User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return; // Authenticated but deleted concurrently — ack without action.
        }
        if (user.isEmailVerified()) {
            log.info("Verification resend skipped: address already verified");
            return; // 202 without side effect; state is visible via /users/me.
        }

        lastSentAt.put(userId, clock.instant());
        final String rawToken = newRawToken();
        accountTokenRepository.save(AccountToken.issue(
                userId, AccountTokenPurpose.EMAIL_VERIFICATION,
                rawToken, emailSettings.verificationTtl(), clock.instant()));

        try {
            emailSenderPort.sendEmailVerificationEmail(user.getProfile().email(), rawToken);
        } catch (RuntimeException e) {
            log.error("Failed to deliver verification e-mail to {}", user.getProfile().email(), e);
        }
    }

    private void enforceCooldown(final UserId userId) {
        final Instant previous = lastSentAt.get(userId);
        if (previous != null) {
            final Instant now = clock.instant();
            if (now.isBefore(previous.plus(RESEND_COOLDOWN))) {
                throw new TooManyRequestsException(
                        "A verification e-mail was recently sent. Try again later.");
            }
        }
    }

    private String newRawToken() {
        final byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
