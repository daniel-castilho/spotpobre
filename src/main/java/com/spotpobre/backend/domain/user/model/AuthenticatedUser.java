package com.spotpobre.backend.domain.user.model;

/**
 * Result of a successful authentication. Pure domain type carrying the authenticated
 * {@link User} (whose {@code roles} are the claims the web layer uses to build tokens).
 * Free of any framework type.
 */
public record AuthenticatedUser(User user) {

    public AuthenticatedUser {
        if (user == null) {
            throw new IllegalArgumentException("Authenticated user cannot be null.");
        }
    }
}