package com.spotpobre.backend.application.user.port.in;

public interface RequestPasswordRecoveryUseCase {

    /**
     * Always acknowledges without revealing whether the e-mail exists (no account enumeration):
     * unknown addresses return the same acknowledgement and send nothing.
     */
    void requestRecovery(final RequestPasswordRecoveryCommand command);

    record RequestPasswordRecoveryCommand(String email) {
    }
}
