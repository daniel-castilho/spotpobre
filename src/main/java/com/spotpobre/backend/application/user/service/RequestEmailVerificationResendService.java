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
import com.spotpobre.backend.domain.user.port.VerificationResendCooldownPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@RequiredArgsConstructor
@Service
public class RequestEmailVerificationResendService implements RequestEmailVerificationResendUseCase {

    private final UserRepository userRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final EmailSenderPort emailSenderPort;
    private final VerificationResendCooldownPort resendCooldownPort;
    private final Clock clock;
    private final EmailVerificationSettings emailSettings;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public void requestResend(final RequestEmailVerificationResendCommand command) {
        final UserId userId = command.userId();
        if (!resendCooldownPort.tryAcquire(userId.value().toString())) {
            throw new TooManyRequestsException(
                    "A verification e-mail was recently sent. Try again later.");
        }

        final User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return; // Authenticated but deleted concurrently — ack without action.
        }
        if (user.isEmailVerified()) {
            log.info("Verification resend skipped: address already verified");
            return; // 202 without side effect; state is visible via /users/me.
        }

        final String rawToken = newRawToken();
        accountTokenRepository.save(AccountToken.issue(
                userId, AccountTokenPurpose.EMAIL_VERIFICATION,
                rawToken, emailSettings.verificationTtl(), clock.instant()));

        try {
            emailSenderPort.sendEmailVerificationEmail(user.getProfile().email(), rawToken);
        } catch (RuntimeException e) {
            log.error("Failed to deliver verification e-mail to {}",
                    com.spotpobre.backend.domain.common.Redaction.maskEmail(
                            user.getProfile().email()), e);
        }
    }

    private String newRawToken() {
        final byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
