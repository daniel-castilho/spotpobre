package com.spotpobre.backend.domain.idempotency.model;

import com.spotpobre.backend.domain.common.Digests;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * One-time lease token proving ownership of an {@code IN_PROGRESS} idempotency claim.
 *
 * <p>The raw token lives only in memory of the claimant; only its SHA-256 hash
 * ({@link #hash()}) may be persisted. Completion/failure/release writes are conditional on the
 * stored hash matching, so a stale claimant can never overwrite a record taken over by another
 * instance. Tokens are never exposed to clients.</p>
 *
 * <p>Pure Java — no framework types.</p>
 */
public final class LeaseToken {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String token;
    private final String tokenHash;

    private LeaseToken(final String token, final String tokenHash) {
        this.token = token;
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash is required");
    }

    private LeaseToken(final String token) {
        this(Objects.requireNonNull(token, "token is required"), Digests.sha256Hex(token));
    }

    public static LeaseToken generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return new LeaseToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /**
     * Reconstructs a token reference from a persisted hash (e.g. re-issuing conditional writes
     * after an in-memory failure). Only equality/hash semantics are meaningful; the raw token is
     * unavailable by design.
     */
    public static LeaseToken fromHash(final String persistedTokenHash) {
        if (persistedTokenHash == null || !persistedTokenHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Persisted lease token hash must be SHA-256 hex");
        }
        return new LeaseToken(null, persistedTokenHash);
    }

    /**
     * @return the raw token — never persist or log this value. Unavailable for hash-only
     *         references created by {@link #fromHash(String)}.
     */
    public String token() {
        if (token == null) {
            throw new IllegalStateException("Raw token is not available for a hash-only reference");
        }
        return token;
    }

    /** @return the SHA-256 hex hash that is safe to persist. */
    public String hash() {
        return tokenHash;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LeaseToken other)) {
            return false;
        }
        // Identity is the persisted hash: raw tokens never leave memory, and hash-only
        // references must equal their originating token.
        return tokenHash.equals(other.tokenHash);
    }

    @Override
    public int hashCode() {
        return token.hashCode();
    }

    @Override
    public String toString() {
        // Never leak the raw token through logs/toString.
        return "LeaseToken{hash=" + tokenHash.substring(0, 10) + "…}";
    }
}
