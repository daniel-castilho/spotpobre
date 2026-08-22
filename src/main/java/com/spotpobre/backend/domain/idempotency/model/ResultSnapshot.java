package com.spotpobre.backend.domain.idempotency.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Safe, replayable result snapshot stored with a {@code COMPLETED} idempotency record.
 *
 * <p>Safety invariants (enforced): the snapshot body never contains presigned/absolute HTTPS
 * URLs or JWTs (both are credentials), the content type is allowlisted, and the payload is
 * size-bounded. No secrets ever reach the durable store.</p>
 *
 * <p>Pure Java — no framework types.</p>
 */
public final class ResultSnapshot {

    public static final int MAX_BODY_LENGTH = 8192;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("application/json");

    private final Integer responseStatus;
    private final String responseContentType;
    private final String location;
    private final String body;

    private ResultSnapshot(final Integer responseStatus, final String responseContentType,
                           final String location, final String body) {
        Objects.requireNonNull(responseStatus, "responseStatus is required");
        if (responseStatus < 200 || responseStatus >= 300) {
            throw new IllegalArgumentException("Result snapshot must be a 2xx status: " + responseStatus);
        }
        this.responseStatus = responseStatus;

        if (responseContentType != null && !ALLOWED_CONTENT_TYPES.contains(responseContentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Response content type not allowlisted: " + responseContentType);
        }
        this.responseContentType = responseContentType == null ? null : responseContentType.toLowerCase(Locale.ROOT);

        if (location != null && (location.contains("://") || !location.startsWith("/"))) {
            throw new IllegalArgumentException("Location must be a relative URI without scheme");
        }
        this.location = location;

        if (body != null) {
            requireSafe(body);
        }
        this.body = body;
    }

    public static ResultSnapshot of(final int responseStatus, final String responseContentType,
                                    final String location, final String body) {
        return new ResultSnapshot(responseStatus, responseContentType, location, body);
    }

    /** Body-only JSON snapshot. */
    public static ResultSnapshot jsonBody(final String body) {
        return new ResultSnapshot(200, "application/json", null, body);
    }

    private static void requireSafe(final String candidate) {
        Objects.requireNonNull(candidate, "body is required");
        if (candidate.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("Snapshot body exceeds " + MAX_BODY_LENGTH + " characters");
        }
        String lowered = candidate.toLowerCase(Locale.ROOT);
        if (lowered.contains("https://") || lowered.contains("http://")) {
            throw new IllegalArgumentException("Snapshot must not contain absolute URLs (signed URL leak)");
        }
        if (candidate.contains("eyJ")) {
            throw new IllegalArgumentException("Snapshot must not contain JWTs");
        }
        if (lowered.contains("password")) {
            throw new IllegalArgumentException("Snapshot must not contain credentials");
        }
    }

    public Integer responseStatus() {
        return responseStatus;
    }

    public String responseContentType() {
        return responseContentType;
    }

    public String location() {
        return location;
    }

    public String body() {
        return body;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResultSnapshot other)) {
            return false;
        }
        return responseStatus.equals(other.responseStatus)
                && Objects.equals(responseContentType, other.responseContentType)
                && Objects.equals(location, other.location)
                && Objects.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(responseStatus, responseContentType, location, body);
    }

    @Override
    public String toString() {
        return "ResultSnapshot{status=" + responseStatus + ", contentType=" + responseContentType
                + ", location=" + location + ", bodyLength=" + (body == null ? 0 : body.length()) + '}';
    }
}
