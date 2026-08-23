package com.spotpobre.backend.domain.common;

import java.util.regex.Pattern;

/**
 * Validated idempotency key supplied by clients on mutating requests.
 *
 * <p>Protocol rules: 16–128 characters, ASCII only, restricted to letters, digits and
 * {@code . _ : -}. Pure domain type — no framework annotations.</p>
 */
public final class IdempotencyKey {

    public static final int MIN_LENGTH = 16;
    public static final int MAX_LENGTH = 128;
    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9._:-]+$");

    private final String value;

    private IdempotencyKey(final String value) {
        this.value = value;
    }

    /**
     * Validates the raw client-supplied header value.
     *
     * @throws IllegalArgumentException if null/blank (missing), out of bounds, or contains
     *                                  disallowed characters — all map to HTTP 400
     */
    public static IdempotencyKey of(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        if (raw.length() < MIN_LENGTH || raw.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        if (!ALLOWED.matcher(raw).matches()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key may only contain ASCII letters, digits and . _ : -");
        }
        return new IdempotencyKey(raw);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdempotencyKey other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
