package com.spotpobre.backend.domain.user.port;

import com.spotpobre.backend.domain.user.model.AuthenticatedUser;

/**
 * Outbound port for authenticating a user against a raw password. The application layer depends
 * only on this contract — never on a concrete authentication provider — so the mechanism (Spring
 * Security, a custom provider, an external IdP, ...) can be swapped by exchanging the adapter.
 */
public interface AuthenticationPort {

    /**
     * Authenticates {@code rawPassword} for the account identified by {@code email}.
     *
     * @throws RuntimeException if the credentials are invalid or the authenticated user cannot be
     *         resolved (adapter-specific exception type).
     */
    AuthenticatedUser authenticate(String email, String rawPassword);
}