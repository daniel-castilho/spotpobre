package com.spotpobre.backend.infrastructure.persistence.kv.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

/**
 * Durable idempotency record. Only digests and safe snapshots are stored — raw keys, e-mails,
 * IPs, JWTs, signed URLs and secrets are structurally excluded by the domain value objects.
 * The {@code expiresAtEpochSeconds} attribute is the DynamoDB TTL attribute.
 */
@DynamoDbBean
public class IdempotencyRecordDocument {

    private String scopeKey;
    private String operationName;
    private String routeTemplate;
    private String actorScopeHash;
    private Integer hashVersion;
    private String requestHash;
    private String state;
    private String resourceType;
    private String resourceId;
    private String leaseTokenHash;
    private Instant leaseUntil;
    private String resultSnapshot;
    private Integer responseStatus;
    private String responseContentType;
    private String location;
    private Integer failureStatus;
    private String failureType;
    private String failureMessage;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private Long expiresAtEpochSeconds;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("scopeKey")
    public String getScopeKey() {
        return scopeKey;
    }

    public void setScopeKey(String scopeKey) {
        this.scopeKey = scopeKey;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public String getRouteTemplate() {
        return routeTemplate;
    }

    public void setRouteTemplate(String routeTemplate) {
        this.routeTemplate = routeTemplate;
    }

    public String getActorScopeHash() {
        return actorScopeHash;
    }

    public void setActorScopeHash(String actorScopeHash) {
        this.actorScopeHash = actorScopeHash;
    }

    public Integer getHashVersion() {
        return hashVersion;
    }

    public void setHashVersion(Integer hashVersion) {
        this.hashVersion = hashVersion;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getLeaseTokenHash() {
        return leaseTokenHash;
    }

    public void setLeaseTokenHash(String leaseTokenHash) {
        this.leaseTokenHash = leaseTokenHash;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public void setLeaseUntil(Instant leaseUntil) {
        this.leaseUntil = leaseUntil;
    }

    public String getResultSnapshot() {
        return resultSnapshot;
    }

    public void setResultSnapshot(String resultSnapshot) {
        this.resultSnapshot = resultSnapshot;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseContentType() {
        return responseContentType;
    }

    public void setResponseContentType(String responseContentType) {
        this.responseContentType = responseContentType;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getFailureStatus() {
        return failureStatus;
    }

    public void setFailureStatus(Integer failureStatus) {
        this.failureStatus = failureStatus;
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    @DynamoDbAttribute("expiresAtEpochSeconds")
    public Long getExpiresAtEpochSeconds() {
        return expiresAtEpochSeconds;
    }

    public void setExpiresAtEpochSeconds(Long expiresAtEpochSeconds) {
        this.expiresAtEpochSeconds = expiresAtEpochSeconds;
    }
}
