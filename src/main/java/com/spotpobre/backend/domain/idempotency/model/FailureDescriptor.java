package com.spotpobre.backend.domain.idempotency.model;

import java.util.Objects;

/**
 * Deterministic post-claim failure stored with a {@code FAILED_FINAL} idempotency record so a
 * retry with the same key replays the same failure instead of re-executing the operation.
 *
 * <p>Only deterministic 4xx failures may become final. Infrastructure/unknown 5xx failures
 * must not use this type — the claim is retained under its lease so a retry can recover.</p>
 */
public final class FailureDescriptor {

    private final int status;
    private final String type;
    private final String message;

    private FailureDescriptor(final int status, final String type, final String message) {
        Objects.requireNonNull(type, "type is required");
        if (status < 400 || status >= 500) {
            throw new IllegalArgumentException(
                    "Only deterministic 4xx failures can be marked final; got " + status);
        }
        this.status = status;
        this.type = type;
        this.message = message == null ? "" : message;
    }

    public static FailureDescriptor of(final int status, final String type, final String message) {
        return new FailureDescriptor(status, type, message);
    }

    public int status() {
        return status;
    }

    public String type() {
        return type;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FailureDescriptor other)) {
            return false;
        }
        return status == other.status && type.equals(other.type) && message.equals(other.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, type, message);
    }
}
