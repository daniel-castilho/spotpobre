package com.spotpobre.backend.domain.idempotency.model;

import com.spotpobre.backend.domain.common.Digests;

import java.util.List;
import java.util.Objects;

/**
 * Versioned SHA-256 hash over the canonical representation of an operation's validated and
 * normalized inputs (command fields in canonical order, canonical path parameters, relevant
 * content type, owner/target IDs). The raw body and raw hash inputs are never persisted or
 * logged.
 *
 * <p>Two requests with the same scope key but a different request hash mean the client reused
 * an Idempotency-Key for different content → deterministic 409.</p>
 *
 * <p>Pure Java — no framework types.</p>
 */
public final class CanonicalRequestHash {

    public static final int CURRENT_VERSION = 1;
    private static final String SEPARATOR = "\u001f"; // ASCII unit separator

    private final int version;
    private final String value;

    private CanonicalRequestHash(final int version, final String value) {
        this.version = version;
        this.value = Objects.requireNonNull(value, "value is required");
    }

    /**
     * @param version hash algorithm version stored with the record
     * @param canonicalFields ordered normalized field values (null becomes empty string)
     */
    public static CanonicalRequestHash of(final int version, final List<String> canonicalFields) {
        Objects.requireNonNull(canonicalFields, "canonicalFields is required");
        if (version < 1 || version > CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unknown request-hash version: " + version + " (current: " + CURRENT_VERSION + ")");
        }
        StringBuilder canonical = new StringBuilder();
        for (int i = 0; i < canonicalFields.size(); i++) {
            if (i > 0) {
                canonical.append(SEPARATOR);
            }
            String field = canonicalFields.get(i);
            canonical.append(field == null ? "" : field);
        }
        return new CanonicalRequestHash(version, Digests.sha256Hex(canonical.toString()));
    }

    public static CanonicalRequestHash current(final List<String> canonicalFields) {
        return of(CURRENT_VERSION, canonicalFields);
    }

    /**
     * Rehydrates a hash from its persisted parts (version + SHA-256 hex value) without
     * re-hashing — the inverse of what adapters must store and read back verbatim.
     */
    public static CanonicalRequestHash ofPersisted(final int version, final String persistedHexValue) {
        if (version < 1 || version > CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unknown request-hash version: " + version + " (current: " + CURRENT_VERSION + ")");
        }
        if (persistedHexValue == null || !persistedHexValue.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Persisted request-hash value must be SHA-256 hex");
        }
        return new CanonicalRequestHash(version, persistedHexValue);
    }

    public int version() {
        return version;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CanonicalRequestHash other)) {
            return false;
        }
        return version == other.version && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, value);
    }

    @Override
    public String toString() {
        return "CanonicalRequestHash{v" + version + ", " + value.substring(0, 10) + "…}";
    }
}
