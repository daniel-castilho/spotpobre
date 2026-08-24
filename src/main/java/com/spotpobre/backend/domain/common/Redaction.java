package com.spotpobre.backend.domain.common;

/**
 * Log-redaction helpers (spec section 12 / defect #15): raw e-mails, cache keys and storage
 * keys must never reach logs, metrics or records verbatim. Masks are stable and short so
 * operators can still correlate entries.
 */
public final class Redaction {

    private Redaction() {
    }

    /** {@code user@example.com} -> {@code u***@example.com}; blank input stays blank. */
    public static String maskEmail(final String email) {
        if (email == null || email.isBlank()) {
            return "<blank>";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** Keeps a short prefix of an identifier for correlation without exposing it. */
    public static String digest(final String value) {
        if (value == null || value.isBlank()) {
            return "<blank>";
        }
        String trimmed = value.strip();
        int keep = Math.min(8, trimmed.length());
        return trimmed.substring(0, keep) + "…";
    }

    /** Storage keys keep their folder prefix only: {@code pending/abcd1234…}. */
    public static String shortStorageKey(final String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return "<blank>";
        }
        int slash = storageKey.lastIndexOf('/');
        String folder = slash >= 0 ? storageKey.substring(0, slash + 1) : "";
        String name = slash >= 0 ? storageKey.substring(slash + 1) : storageKey;
        int keep = Math.min(8, name.length());
        return folder + name.substring(0, keep) + "…";
    }
}
