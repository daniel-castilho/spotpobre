package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.RequestPasswordRecoveryUseCase;
import com.spotpobre.backend.domain.user.model.AccountToken;
import com.spotpobre.backend.domain.user.model.AccountTokenPurpose;
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
import java.util.Base64;

@Slf4j
@RequiredArgsConstructor
@Service
public class RequestPasswordRecoveryService implements RequestPasswordRecoveryUseCase {

    static final Duration TOKEN_TTL = AccountToken.DEFAULT_TTL;

    private final UserRepository userRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final EmailSenderPort emailSenderPort;
    private final Clock clock;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public void requestRecovery(final RequestPasswordRecoveryCommand command) {
        // Same acknowledgement either way: no account enumeration.
        userRepository.findByProfileEmail(command.email()).ifPresentOrElse(
                user -> issueAndSend(user.getId(), user.getProfile().email()),
                () -> log.info("Password recovery requested for unknown e-mail"));
    }

    private void issueAndSend(final UserId userId, final String email) {
        final String rawToken = newRawToken();
        accountTokenRepository.save(AccountToken.issue(
                userId, AccountTokenPurpose.PASSWORD_RESET, rawToken, TOKEN_TTL, clock.instant()));

        // Semantic port call: subjects/bodies/links belong to the delivery adapter.
        try {
            emailSenderPort.sendPasswordRecoveryEmail(email, rawToken);
        } catch (RuntimeException e) {
            // Never surface provider failures to the caller (enumeration-safe ack); the token
            // simply expires unused and the user can request a new one.
            log.error("Failed to deliver password recovery e-mail to {}",
                    com.spotpobre.backend.infrastructure.common.Redaction.maskEmail(email), e);
        }
    }

    private String newRawToken() {
        final byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
