package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.domain.idempotency.model.CanonicalRequestHash;
import com.spotpobre.backend.domain.idempotency.model.FailureDescriptor;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyRecord;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyResourceType;
import com.spotpobre.backend.domain.idempotency.model.IdempotencyState;
import com.spotpobre.backend.domain.idempotency.model.LeaseToken;
import com.spotpobre.backend.domain.idempotency.model.ResultSnapshot;
import com.spotpobre.backend.domain.idempotency.port.IdempotencyRecordRepository;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.IdempotencyRecordDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB adapter for the durable idempotency store. Every lifecycle transition is a
 * conditional write so concurrent claimants converge: at most one caller can hold the lease,
 * terminal states are never overwritten, and takeover preserves the reserved resource ID.
 *
 * <p>Transitions that mutate a subset of fields are implemented as a read followed by a
 * full-item {@code update} whose {@code ConditionExpression} re-checks the observed
 * {@code state} and {@code leaseTokenHash}. Because the condition is evaluated atomically with
 * the write, any interleaving writer (takeover, replacement) invalidates the stale copy and the
 * transition fails cleanly ({@code false}) instead of corrupting the record.</p>
 */
@Component
@RequiredArgsConstructor
public class DynamoDbIdempotencyRecordRepositoryAdapter implements IdempotencyRecordRepository {

    private final DynamoDbTable<IdempotencyRecordDocument> table;

    private static Key key(final String scopeKey) {
        return Key.builder().partitionValue(scopeKey).build();
    }

    @Override
    public Optional<IdempotencyRecord> findByScopeKey(final String scopeKey) {
        return Optional.ofNullable(table.getItem(key(scopeKey))).map(this::toDomain);
    }

    @Override
    public boolean insertInProgress(final IdempotencyRecord record) {
        try {
            table.putItem(PutItemEnhancedRequest.builder(IdempotencyRecordDocument.class)
                    .item(toDocument(record))
                    .conditionExpression(Expression.builder()
                            .expression("attribute_not_exists(scopeKey)")
                            .build())
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public boolean takeoverExpiredLease(final String scopeKey, final CanonicalRequestHash expectedRequestHash,
                                        final LeaseToken newLease, final Instant newLeaseUntil,
                                        final Instant now) {
        IdempotencyRecordDocument observed = table.getItem(key(scopeKey));
        if (observed == null || !IdempotencyState.IN_PROGRESS.name().equals(observed.getState())) {
            return false;
        }

        // CAS guard: the write only lands if the record still carries exactly the lease we
        // observed (compare-and-swap), so two racing takeovers cannot both succeed.
        final String observedLeaseHash = observed.getLeaseTokenHash();
        final String observedRequestHash = observed.getRequestHash();

        if (!expectedRequestHash.value().equals(observedRequestHash)) {
            return false;
        }

        observed.setLeaseTokenHash(newLease.hash());
        observed.setLeaseUntil(newLeaseUntil);
        observed.setUpdatedAt(now);

        try {
            table.updateItem(UpdateItemEnhancedRequest.builder(IdempotencyRecordDocument.class)
                    .item(observed)
                    .conditionExpression(Expression.builder()
                            // leaseUntil persists as an ISO-8601 string (Instant -> S), so the
                            // deadline comparison must be string-vs-string; ISO-8601 UTC strings
                            // sort chronologically.
                            .expression("#st = :inProgress AND #lu <= :now AND #lth = :observedLeaseHash")
                            .expressionNames(Map.of(
                                    "#st", "state",
                                    "#lu", "leaseUntil",
                                    "#lth", "leaseTokenHash"))
                            .expressionValues(Map.of(
                                    ":inProgress", s(IdempotencyState.IN_PROGRESS.name()),
                                    ":now", s(now.toString()),
                                    ":observedLeaseHash", s(observedLeaseHash)))
                            .build())
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public boolean replaceLogicallyExpired(final String scopeKey, final IdempotencyRecord replacement,
                                           final Instant now) {
        try {
            table.putItem(PutItemEnhancedRequest.builder(IdempotencyRecordDocument.class)
                    .item(toDocument(replacement))
                    .conditionExpression(Expression.builder()
                            .expression("attribute_exists(scopeKey) AND expiresAtEpochSeconds <= :expiredAt")
                            .expressionValues(Map.of(":expiredAt", n(now.getEpochSecond())))
                            .build())
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public boolean markCompleted(final String scopeKey, final LeaseToken currentLease,
                                 final ResultSnapshot snapshot, final Instant completedAt,
                                 final Instant updatedAt) {
        IdempotencyRecordDocument observed = table.getItem(key(scopeKey));
        if (observed == null || !IdempotencyState.IN_PROGRESS.name().equals(observed.getState())) {
            return false;
        }
        if (!currentLease.hash().equals(observed.getLeaseTokenHash())) {
            return false;
        }

        observed.setState(IdempotencyState.COMPLETED.name());
        if (snapshot != null) {
            observed.setResultSnapshot(snapshot.body());
            observed.setResponseStatus(snapshot.responseStatus());
            observed.setResponseContentType(snapshot.responseContentType());
            observed.setLocation(snapshot.location());
        }
        observed.setCompletedAt(completedAt);
        observed.setUpdatedAt(updatedAt);

        return conditionalWriteWhileLeased(observed, currentLease);
    }

    @Override
    public boolean markFailedFinal(final String scopeKey, final LeaseToken currentLease,
                                   final FailureDescriptor failure, final Instant at) {
        IdempotencyRecordDocument observed = table.getItem(key(scopeKey));
        if (observed == null || !IdempotencyState.IN_PROGRESS.name().equals(observed.getState())) {
            return false;
        }
        if (!currentLease.hash().equals(observed.getLeaseTokenHash())) {
            return false;
        }

        observed.setState(IdempotencyState.FAILED_FINAL.name());
        observed.setFailureStatus(failure.status());
        observed.setFailureType(failure.type());
        observed.setFailureMessage(failure.message());
        observed.setUpdatedAt(at);

        return conditionalWriteWhileLeased(observed, currentLease);
    }

    @Override
    public boolean releaseInProgress(final String scopeKey, final LeaseToken currentLease) {
        try {
            table.deleteItem(DeleteItemEnhancedRequest.builder()
                    .key(key(scopeKey))
                    .conditionExpression(Expression.builder()
                            .expression("#st = :inProgress AND #lth = :expectedLeaseHash")
                            .expressionNames(Map.of(
                                    "#st", "state",
                                    "#lth", "leaseTokenHash"))
                            .expressionValues(Map.of(
                                    ":inProgress", s(IdempotencyState.IN_PROGRESS.name()),
                                    ":expectedLeaseHash", s(currentLease.hash())))
                            .build())
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    /**
     * Writes the mutated document back only while the record is still IN_PROGRESS and still
     * leased to exactly the token the caller observed. Any competing transition (another lease
     * holder completing, a takeover swapping the token) fails this condition.
     */
    private boolean conditionalWriteWhileLeased(final IdempotencyRecordDocument mutated,
                                                final LeaseToken currentLease) {
        try {
            table.updateItem(UpdateItemEnhancedRequest.builder(IdempotencyRecordDocument.class)
                    .item(mutated)
                    .conditionExpression(Expression.builder()
                            .expression("#st = :inProgress AND #lth = :expectedLeaseHash")
                            .expressionNames(Map.of(
                                    "#st", "state",
                                    "#lth", "leaseTokenHash"))
                            .expressionValues(Map.of(
                                    ":inProgress", s(IdempotencyState.IN_PROGRESS.name()),
                                    ":expectedLeaseHash", s(currentLease.hash())))
                            .build())
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    private IdempotencyRecord toDomain(final IdempotencyRecordDocument doc) {
        IdempotencyRecord.Builder builder = IdempotencyRecord.builder()
                .scopeKey(doc.getScopeKey())
                .operationName(doc.getOperationName())
                .routeTemplate(doc.getRouteTemplate())
                .actorScopeHash(doc.getActorScopeHash())
                .requestHash(CanonicalRequestHash.ofPersisted(
                        doc.getHashVersion() == null ? CanonicalRequestHash.CURRENT_VERSION : doc.getHashVersion(),
                        doc.getRequestHash() == null ? "" : doc.getRequestHash()))
                .state(IdempotencyState.valueOf(doc.getState()))
                .resourceType(IdempotencyResourceType.valueOf(doc.getResourceType()))
                .resourceId(doc.getResourceId())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .expiresAtEpochSeconds(doc.getExpiresAtEpochSeconds());

        if (doc.getCompletedAt() != null) {
            builder.completedAt(doc.getCompletedAt());
        }
        if (doc.getLeaseTokenHash() != null) {
            builder.lease(LeaseToken.fromHash(doc.getLeaseTokenHash())).leaseUntil(doc.getLeaseUntil());
        }
        if (doc.getResultSnapshot() != null || doc.getResponseStatus() != null) {
            builder.resultSnapshot(ResultSnapshot.of(
                    doc.getResponseStatus(),
                    doc.getResponseContentType(),
                    doc.getLocation(),
                    doc.getResultSnapshot()));
        }
        if (doc.getFailureStatus() != null) {
            builder.failure(FailureDescriptor.of(
                    doc.getFailureStatus(), doc.getFailureType(), doc.getFailureMessage()));
        }
        return builder.build();
    }

    private IdempotencyRecordDocument toDocument(final IdempotencyRecord record) {
        IdempotencyRecordDocument doc = new IdempotencyRecordDocument();
        doc.setScopeKey(record.scopeKey());
        doc.setOperationName(record.operationName());
        doc.setRouteTemplate(record.routeTemplate());
        doc.setActorScopeHash(record.actorScopeHash());
        doc.setHashVersion(record.requestHash().version());
        doc.setRequestHash(record.requestHash().value());
        doc.setState(record.state().name());
        doc.setResourceType(record.resourceType().name());
        doc.setResourceId(record.resourceId());
        if (record.lease() != null) {
            doc.setLeaseTokenHash(record.lease().hash());
        }
        doc.setLeaseUntil(record.leaseUntil());
        if (record.resultSnapshot() != null) {
            ResultSnapshot snapshot = record.resultSnapshot();
            doc.setResultSnapshot(snapshot.body());
            doc.setResponseStatus(snapshot.responseStatus());
            doc.setResponseContentType(snapshot.responseContentType());
            doc.setLocation(snapshot.location());
        }
        if (record.failure() != null) {
            doc.setFailureStatus(record.failure().status());
            doc.setFailureType(record.failure().type());
            doc.setFailureMessage(record.failure().message());
        }
        doc.setCreatedAt(record.createdAt());
        doc.setUpdatedAt(record.updatedAt());
        doc.setCompletedAt(record.completedAt());
        doc.setExpiresAtEpochSeconds(record.expiresAtEpochSeconds());
        return doc;
    }

    private static AttributeValue s(final String value) {
        return AttributeValue.builder().s(value == null ? "" : value).build();
    }

    private static AttributeValue n(final Number value) {
        return AttributeValue.builder().n(value == null ? "0" : value.toString()).build();
    }
}
