package com.spotpobre.backend.domain.common;

import java.util.Objects;

/**
 * One-way digest helpers used wherever a raw client-supplied value must never be persisted or
 * logged. Only short, non-reversible correlation digests may appear in logs/metrics.
 *
 * <p>Pure Java — no framework types.</p>
 */
public final class Digests {

    private Digests() {
    }

    /**
     * @return lowercase hex SHA-256 of the UTF-8 input.
     */
    public static String sha256Hex(final String raw) {
        Objects.requireNonNull(raw, "raw value is required");
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /**
     * @return a short non-reversible correlation digest (first {@code chars} of the SHA-256 hex)
     *          for use in logs and metrics instead of the raw value.
     */
    public static String shortDigest(final String raw) {
        return sha256Hex(raw).substring(0, 10);
    }
}
