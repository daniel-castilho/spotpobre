package com.spotpobre.backend.application.idempotency.port.out;

/**
 * Low-cardinality metrics for the idempotency protocol. Outbound application port —
 * infrastructure implements it (e.g. Micrometer counters); tags are strictly bounded enums so
 * cardinality cannot explode and no PII ever becomes a label.
 */
public interface IdempotencyMetrics {

    /** Counts claim attempts by terminal outcome of the attempt. */
    void incrementClaimOutcome(ClaimOutcomeTag outcome);

    /** Counts conditional completions/failures/releases attempted by a lease holder. */
    void incrementTransition(TransitionTag transition);

    enum ClaimOutcomeTag {
        CLAIMED_NEW,
        RECOVERED_CLAIM,
        REPLAY_COMPLETED,
        REPLAY_FAILED_FINAL,
        ACTIVE_LEASE,
        KEY_REUSED
    }

    enum TransitionTag {
        COMPLETED,
        FAILED_FINAL,
        RELEASED,
        LOST_LEASE
    }
}
