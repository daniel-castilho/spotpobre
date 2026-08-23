package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.ConfirmEmailVerificationUseCase;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.user.model.AccountToken;
import com.spotpobre.backend.domain.user.model.AccountTokenPurpose;
import com.spotpobre.backend.domain.user.port.AccountTokenRepository;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Burn-first confirmation: the conditional redemption wins the race, then the flag is persisted.
 * These are two separate DynamoDB writes (NOT one atomic transaction) — the chosen intermediate
 * states are all safe: if the process dies in between, the token is burnt and the user simply
 * requests a resend. A replay of the same confirm after success answers 404 like any redeemed
 * token.
 */
@RequiredArgsConstructor
@Service
public class ConfirmEmailVerificationService implements ConfirmEmailVerificationUseCase {

    private final AccountTokenRepository accountTokenRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void confirm(final ConfirmEmailVerificationCommand command) {
        final String tokenHash = AccountToken.hashOf(command.rawToken());
        final AccountToken token = accountTokenRepository
                .findActiveByHash(tokenHash, AccountTokenPurpose.EMAIL_VERIFICATION)
                .orElseThrow(ConfirmEmailVerificationService::tokenNotFound);

        if (token.isExpiredAt(clock.instant())) {
            throw tokenNotFound();
        }

        // Burn first: concurrent confirmations of the same link cannot both reach the flag write.
        accountTokenRepository.markUsed(tokenHash);

        final var user = userRepository.findById(token.userId())
                .orElseThrow(ConfirmEmailVerificationService::tokenNotFound);
        user.markEmailVerified(clock.instant());
        userRepository.save(user);
    }

    private static NotFoundException tokenNotFound() {
        return new NotFoundException("Invalid or expired verification token");
    }
}
