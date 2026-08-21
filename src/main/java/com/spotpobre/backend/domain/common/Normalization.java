package com.spotpobre.backend.domain.common;

import java.util.Locale;

/**
 * Deterministic input normalization shared by the web boundary and the canonical
 * request fingerprinting. Pure Java — no framework dependencies.
 */
public final class Normalization {

    private Normalization() {
    }

    /**
     * Trims surrounding whitespace. Returns {@code null} for {@code null} input.
     */
    public static String trim(final String raw) {
        return raw == null ? null : raw.trim();
    }

    /**
     * Trims and lowercases using the root locale (locale-independent, stable for hashing).
     */
    public static String lowercase(final String raw) {
        final String trimmed = trim(raw);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * Trims and uppercases using the root locale.
     */
    public static String uppercase(final String raw) {
        final String trimmed = trim(raw);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
