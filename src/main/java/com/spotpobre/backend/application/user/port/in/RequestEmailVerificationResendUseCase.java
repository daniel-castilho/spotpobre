package com.spotpobre.backend.application.user.port.in;

import com.spotpobre.backend.domain.user.model.UserId;

public interface RequestEmailVerificationResendUseCase {

    /**
     * Always acknowledges 202 for the authenticated account; silently skips the e-mail when the
     * address is already verified, and applies a per-user cooldown between sends.
     */
    void requestResend(final RequestEmailVerificationResendCommand command);

    record RequestEmailVerificationResendCommand(UserId userId) {
    }
}
