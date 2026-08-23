package com.spotpobre.backend.application.user.port.in;

import com.spotpobre.backend.domain.user.model.User;

/**
 * Registration protected by the durable idempotency protocol: the caller must present an
 * {@code Idempotency-Key}; retries with the same key and the same canonical request resolve the
 * same user (a fresh JWT is minted by the web layer on replay — tokens are never stored).
 */
public interface RegisterUserIdempotentlyUseCase {
    RegistrationOutcome registerIdempotently(final String rawIdempotencyKey,
                                             final RegisterUserUseCase.RegisterUserCommand command);

    /**
     * @param user     the registered user (either freshly created or recovered for replay)
     * @param replayed {@code true} when this outcome is a replay of a previously completed
     *                 execution; drives the {@code Idempotency-Replayed} response header
     */
    record RegistrationOutcome(User user, boolean replayed) {
    }
}
