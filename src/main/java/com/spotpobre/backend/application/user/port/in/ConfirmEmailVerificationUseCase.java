package com.spotpobre.backend.application.user.port.in;

public interface ConfirmEmailVerificationUseCase {

    /**
     * Redeems a single-use verification token and stamps {@code emailVerifiedAt} on the account.
     *
     * @throws com.spotpobre.backend.domain.common.NotFoundException for unknown, expired or
     * already-redeemed tokens (same answer — tokens are secrets).
     */
    void confirm(final ConfirmEmailVerificationCommand command);

    record ConfirmEmailVerificationCommand(String rawToken) {
    }
}
