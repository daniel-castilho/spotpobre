package com.spotpobre.backend.infrastructure.persistence.kv.entity;

import java.time.Instant;

/**
 * Persistence shape of an account-lifecycle token (see domain {@code AccountToken}). Only the
 * SHA-256 hash of the raw value is stored; {@code expiresAtEpochSeconds} drives DynamoDB TTL.
 */
public class AccountTokenDocument {

    private String tokenHash;
    private String userId;
    private String purpose;
    private Long expiresAtEpochSeconds;
    private Long usedAtEpochSeconds;
    private Instant createdAt;

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(final String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(final String purpose) {
        this.purpose = purpose;
    }

    public Long getExpiresAtEpochSeconds() {
        return expiresAtEpochSeconds;
    }

    public void setExpiresAtEpochSeconds(final Long expiresAtEpochSeconds) {
        this.expiresAtEpochSeconds = expiresAtEpochSeconds;
    }

    public Long getUsedAtEpochSeconds() {
        return usedAtEpochSeconds;
    }

    public void setUsedAtEpochSeconds(final Long usedAtEpochSeconds) {
        this.usedAtEpochSeconds = usedAtEpochSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }
}
