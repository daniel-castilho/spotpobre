package com.spotpobre.backend.application.user.port.in;

public interface ResetPasswordUseCase {

    /**
     * Redeems a single-use recovery token: replaces the account password with a freshly encoded
     * one and burns the token.
     *
     * @throws com.spotpobre.backend.domain.common.NotFoundException for unknown, expired or
     * already-redeemed tokens (same answer, no distinction — tokens are secrets).
     */
    void resetPassword(final ResetPasswordCommand command);

    record ResetPasswordCommand(String rawToken, String newPassword) {
    }
}
