package com.spotpobre.backend.domain.user.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Single-use account-lifecycle token (password recovery, email verification). Only the SHA-256
 * hash of the raw value is ever persisted — the raw value exists solely inside the emailed link,
 * mirroring the idempotency store's digest-only rule.
 *
 * @param userId    owner of the token
 * @param purpose   lifecycle flow this token may redeem
 * @param tokenHash lowercase hex SHA-256 of the raw token value
 * @param expiresAt absolute expiry; consumers must reject tokens at or after this instant
 */
public record AccountToken(UserId userId, AccountTokenPurpose purpose, String tokenHash, Instant expiresAt) {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    public AccountToken {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("purpose is required");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash is required");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }
    }

    /** Hashes a raw token value for storage/lookup. Raw values are never persisted. */
    public static String hashOf(final String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken cannot be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public boolean isExpiredAt(final Instant now) {
        return !expiresAt.isAfter(now);
    }

    /**
     * Issues a token expiring {@code ttl} after {@code now} for the given raw value.
     */
    public static AccountToken issue(final UserId userId, final AccountTokenPurpose purpose,
                                     final String rawToken, final Duration ttl, final Instant now) {
        return new AccountToken(userId, purpose, hashOf(rawToken), now.plus(ttl));
    }
}
