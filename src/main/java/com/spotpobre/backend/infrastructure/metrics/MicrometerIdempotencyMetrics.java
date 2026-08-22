package com.spotpobre.backend.infrastructure.metrics;

import com.spotpobre.backend.application.idempotency.port.out.IdempotencyMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Micrometer implementation of the idempotency metrics port. Counters only — tags are bounded
 * enum values, so no cardinality explosion and no PII can leak into the metrics backend.
 */
@Component
@RequiredArgsConstructor
public class MicrometerIdempotencyMetrics implements IdempotencyMetrics {

    private static final String CLAIM_COUNTER = "spotpobre_idempotency_claims_total";
    private static final String TRANSITION_COUNTER = "spotpobre_idempotency_transitions_total";
    private static final String OUTCOME_TAG = "outcome";
    private static final String TRANSITION_TAG = "transition";

    private final MeterRegistry meterRegistry;

    @Override
    public void incrementClaimOutcome(final IdempotencyMetrics.ClaimOutcomeTag outcome) {
        meterRegistry.counter(CLAIM_COUNTER, OUTCOME_TAG, outcome.name()).increment();
    }

    @Override
    public void incrementTransition(final IdempotencyMetrics.TransitionTag transition) {
        meterRegistry.counter(TRANSITION_COUNTER, TRANSITION_TAG, transition.name()).increment();
    }}
