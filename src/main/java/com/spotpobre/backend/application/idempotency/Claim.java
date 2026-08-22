package com.spotpobre.backend.application.idempotency;

import com.spotpobre.backend.domain.idempotency.model.LeaseToken;

import java.util.Objects;

/**
 * A live claim held by this caller after winning the conditional create/takeover race.
 * Carries everything needed to execute the operation once (stable resource ID) and to finish
 * it conditionally through the {@link IdempotencyCoordinator} (lease token).
 *
 * <p>The raw lease token never leaves process memory; only its hash is persisted.</p>
 */
public final class Claim {

    private final String scopeKey;
    private final String resourceId;
    private final LeaseToken lease;
    private final boolean recoveredFromPreviousAttempt;

    public Claim(final String scopeKey, final String resourceId, final LeaseToken lease,
                 final boolean recoveredFromPreviousAttempt) {
        this.scopeKey = Objects.requireNonNull(scopeKey, "scopeKey is required");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId is required");
        this.lease = Objects.requireNonNull(lease, "lease is required");
        this.recoveredFromPreviousAttempt = recoveredFromPreviousAttempt;
    }

    public String scopeKey() {
        return scopeKey;
    }

    public String resourceId() {
        return resourceId;
    }

    public LeaseToken lease() {
        return lease;
    }

    /** True when takeover/replace restored a previous IN_PROGRESS attempt with the same resource ID. */
    public boolean recoveredFromPreviousAttempt() {
        return recoveredFromPreviousAttempt;
    }
}
