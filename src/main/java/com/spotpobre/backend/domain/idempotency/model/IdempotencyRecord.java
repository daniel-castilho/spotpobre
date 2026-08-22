package com.spotpobre.backend.domain.idempotency.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Durable idempotency record (aggregate root). One record per scope key governs the full claim
 * → execute → complete/fail lifecycle of a logical operation, including crash recovery through
 * lease takeover that preserves the preassigned resource ID.
 *
 * <p>Records carry only digests and safe snapshots: raw keys, e-mails, IPs, JWTs, presigned
 * URLs and secrets are structurally excluded by the value objects.</p>
 *
 * <p>Pure Java — no framework annotations; reconstructed via {@link Builder}.</p>
 */
public class IdempotencyRecord {

    private final String scopeKey;
    private final String operationName;
    private final String routeTemplate;
    private final String actorScopeHash;
    private final CanonicalRequestHash requestHash;
    private final IdempotencyState state;
    private final IdempotencyResourceType resourceType;
    private final String resourceId;
    private final LeaseToken lease;
    private final Instant leaseUntil;
    private final ResultSnapshot resultSnapshot;
    private final FailureDescriptor failure;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant completedAt;
    private final long expiresAtEpochSeconds;

    private IdempotencyRecord(final Builder builder) {
        this.scopeKey = Objects.requireNonNull(builder.scopeKey, "scopeKey is required");
        this.operationName = Objects.requireNonNull(builder.operationName, "operationName is required");
        this.routeTemplate = builder.routeTemplate == null ? "" : builder.routeTemplate;
        this.actorScopeHash = builder.actorScopeHash == null ? "" : builder.actorScopeHash;
        this.requestHash = Objects.requireNonNull(builder.requestHash, "requestHash is required");
        this.state = Objects.requireNonNull(builder.state, "state is required");
        this.resourceType = Objects.requireNonNull(builder.resourceType, "resourceType is required");
        this.resourceId = Objects.requireNonNull(builder.resourceId, "resourceId is required");

        if (builder.state == IdempotencyState.IN_PROGRESS) {
            this.lease = Objects.requireNonNull(builder.lease, "lease is required while IN_PROGRESS");
            this.leaseUntil = Objects.requireNonNull(builder.leaseUntil, "leaseUntil is required while IN_PROGRESS");
        } else {
            this.lease = builder.lease;
            this.leaseUntil = builder.leaseUntil;
        }

        if (builder.state == IdempotencyState.COMPLETED) {
            this.completedAt = Objects.requireNonNull(builder.completedAt, "completedAt is required when COMPLETED");
        } else {
            this.completedAt = builder.completedAt;
        }
        if (builder.state == IdempotencyState.FAILED_FINAL && builder.failure == null) {
            throw new IllegalStateException("failure descriptor is required when FAILED_FINAL");
        }
        this.resultSnapshot = builder.resultSnapshot;
        this.failure = builder.failure;

        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt is required");
        if (builder.expiresAtEpochSeconds <= 0) {
            throw new IllegalArgumentException("expiresAtEpochSeconds must be positive");
        }
        this.expiresAtEpochSeconds = builder.expiresAtEpochSeconds;
    }

    /** Fresh IN_PROGRESS claim with a stable preassigned resource ID and a live lease. */
    static IdempotencyRecord newClaim(final String scopeKey, final String operationName,
                                      final String routeTemplate, final String actorScopeHash,
                                      final CanonicalRequestHash requestHash,
                                      final IdempotencyResourceType resourceType,
                                      final String resourceId, final LeaseToken lease,
                                      final Instant now, final Instant leaseUntil,
                                      final long expiresAtEpochSeconds) {
        return builder()
                .scopeKey(scopeKey)
                .operationName(operationName)
                .routeTemplate(routeTemplate)
                .actorScopeHash(actorScopeHash)
                .requestHash(requestHash)
                .state(IdempotencyState.IN_PROGRESS)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .lease(lease)
                .leaseUntil(leaseUntil)
                .createdAt(now)
                .updatedAt(now)
                .expiresAtEpochSeconds(expiresAtEpochSeconds)
                .build();
    }

    public boolean leaseActiveAt(final Instant now) {
        return leaseUntil != null && leaseUntil.isAfter(now);
    }

    public boolean logicallyExpiredAt(final Instant now) {
        return expiresAtEpochSeconds <= now.getEpochSecond();
    }

    public boolean matches(final CanonicalRequestHash other) {
        return requestHash.equals(other);
    }

    /** Record state after an expired-lease takeover by a new lease token. */
    IdempotencyRecord takenOverBy(final LeaseToken newLease, final Instant now, final Instant newLeaseUntil) {
        return builder().merge(this)
                .lease(newLease)
                .leaseUntil(newLeaseUntil)
                .updatedAt(now)
                .build();
    }

    public String scopeKey() {
        return scopeKey;
    }

    public String operationName() {
        return operationName;
    }

    public String routeTemplate() {
        return routeTemplate;
    }

    public String actorScopeHash() {
        return actorScopeHash;
    }

    public CanonicalRequestHash requestHash() {
        return requestHash;
    }

    public IdempotencyState state() {
        return state;
    }

    public IdempotencyResourceType resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }

    /** Raw lease of the current claimant — never persisted; only the hash is stored. */
    public LeaseToken lease() {
        return lease;
    }

    public Instant leaseUntil() {
        return leaseUntil;
    }

    public ResultSnapshot resultSnapshot() {
        return resultSnapshot;
    }

    public FailureDescriptor failure() {
        return failure;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public long expiresAtEpochSeconds() {
        return expiresAtEpochSeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String scopeKey;
        private String operationName;
        private String routeTemplate;
        private String actorScopeHash;
        private CanonicalRequestHash requestHash;
        private IdempotencyState state;
        private IdempotencyResourceType resourceType;
        private String resourceId;
        private LeaseToken lease;
        private Instant leaseUntil;
        private ResultSnapshot resultSnapshot;
        private FailureDescriptor failure;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant completedAt;
        private long expiresAtEpochSeconds;

        private Builder() {
        }

        /** Copies every attribute from an existing record (used for conditional transitions). */
        public Builder merge(final IdempotencyRecord source) {
            this.scopeKey = source.scopeKey;
            this.operationName = source.operationName;
            this.routeTemplate = source.routeTemplate;
            this.actorScopeHash = source.actorScopeHash;
            this.requestHash = source.requestHash;
            this.state = source.state;
            this.resourceType = source.resourceType;
            this.resourceId = source.resourceId;
            this.lease = source.lease;
            this.leaseUntil = source.leaseUntil;
            this.resultSnapshot = source.resultSnapshot;
            this.failure = source.failure;
            this.createdAt = source.createdAt;
            this.updatedAt = source.updatedAt;
            this.completedAt = source.completedAt;
            this.expiresAtEpochSeconds = source.expiresAtEpochSeconds;
            return this;
        }

        public Builder scopeKey(final String scopeKey) {
            this.scopeKey = scopeKey;
            return this;
        }

        public Builder operationName(final String operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder routeTemplate(final String routeTemplate) {
            this.routeTemplate = routeTemplate;
            return this;
        }

        public Builder actorScopeHash(final String actorScopeHash) {
            this.actorScopeHash = actorScopeHash;
            return this;
        }

        public Builder requestHash(final CanonicalRequestHash requestHash) {
            this.requestHash = requestHash;
            return this;
        }

        public Builder state(final IdempotencyState state) {
            this.state = state;
            return this;
        }

        public Builder resourceType(final IdempotencyResourceType resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(final String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder lease(final LeaseToken lease) {
            this.lease = lease;
            return this;
        }

        public Builder leaseUntil(final Instant leaseUntil) {
            this.leaseUntil = leaseUntil;
            return this;
        }

        public Builder resultSnapshot(final ResultSnapshot resultSnapshot) {
            this.resultSnapshot = resultSnapshot;
            return this;
        }

        public Builder failure(final FailureDescriptor failure) {
            this.failure = failure;
            return this;
        }

        public Builder createdAt(final Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(final Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder completedAt(final Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder expiresAtEpochSeconds(final long expiresAtEpochSeconds) {
            this.expiresAtEpochSeconds = expiresAtEpochSeconds;
            return this;
        }

        public IdempotencyRecord build() {
            return new IdempotencyRecord(this);
        }
    }
}
