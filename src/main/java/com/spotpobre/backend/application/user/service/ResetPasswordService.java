package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.ResetPasswordUseCase;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.user.port.PasswordHasher;
import com.spotpobre.backend.domain.user.model.AccountToken;
import com.spotpobre.backend.domain.user.model.AccountTokenPurpose;
import com.spotpobre.backend.domain.user.port.AccountTokenRepository;
import com.spotpobre.backend.domain.user.port.UserAuthenticationCachePort;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@RequiredArgsConstructor
@Service
public class ResetPasswordService implements ResetPasswordUseCase {

    private final AccountTokenRepository accountTokenRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final UserAuthenticationCachePort authenticationCachePort;
    private final Clock clock;

    @Override
    @Transactional
    public void resetPassword(final ResetPasswordCommand command) {
        final String tokenHash = AccountToken.hashOf(command.rawToken());
        final AccountToken token = accountTokenRepository
                .findActiveByHash(tokenHash, AccountTokenPurpose.PASSWORD_RESET)
                .orElseThrow(ResetPasswordService::tokenNotFound);

        if (token.isExpiredAt(clock.instant())) {
            throw tokenNotFound();
        }

        final var user = userRepository.findById(token.userId())
                .orElseThrow(ResetPasswordService::tokenNotFound);

        user.changePassword(passwordHasher.encode(command.newPassword()), clock.instant());
        userRepository.save(user);

        // Defect #13 hardening: burn every sibling recovery link, then invalidate cached
        // credentials and JWTs issued before the change.
        accountTokenRepository.markAllUsedForUser(token.userId(), AccountTokenPurpose.PASSWORD_RESET,
                clock.instant());
        authenticationCachePort.evict(user.getProfile().email());
    }

    /** Unknown, expired and redeemed tokens are indistinguishable — tokens are secrets. */
    private static NotFoundException tokenNotFound() {
        return new NotFoundException("Invalid or expired recovery token");
    }
}
