package com.spotpobre.backend.domain.user.port;

/**
 * Outbound port for password hashing. The application layer depends only on this contract,
 * never on a concrete hashing library, so the algorithm (BCrypt, Argon2id, scrypt, ...) can be
 * swapped by exchanging the adapter implementation without touching business code.
 */
public interface PasswordHasher {

    /**
     * Hashes a raw password.
     */
    String encode(String rawPassword);

    /**
     * Verifies a raw password against a stored hash.
     */
    boolean matches(String rawPassword, String hashedPassword);
}