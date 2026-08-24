package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.model.SongUpload;
import com.spotpobre.backend.domain.song.model.SongUploadState;
import com.spotpobre.backend.domain.song.port.SongUploadRepository;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongUploadDocument;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * DynamoDB adapter for the durable song-upload protocol. Every transition is a conditional
 * write: racing confirmations or cleanup workers elect exactly one winner and stale holders
 * observe {@code false}.
 */
@Component
public class DynamoDbSongUploadRepositoryAdapter implements SongUploadRepository {

    private final DynamoDbTable<SongUploadDocument> table;

    public DynamoDbSongUploadRepositoryAdapter(final DynamoDbTable<SongUploadDocument> table) {
        this.table = table;
    }

    private static Key key(final SongId songId) {
        return Key.builder().partitionValue(songId.value().toString()).build();
    }

    @Override
    public boolean insertIfAbsent(final SongUpload upload) {
        try {
            table.putItem(r -> r.item(toDocument(upload))
                    .conditionExpression(Expression.builder()
                            .expression("attribute_not_exists(songId)")
                            .build()));
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public Optional<SongUpload> findBySongId(final SongId songId) {
        return Optional.ofNullable(table.getItem(key(songId))).map(this::toDomain);
    }

    @Override
    public boolean acquireCompletingLease(final SongId songId, final Instant newLeaseUntil,
                                          final Instant now) {
        final SongUploadDocument observed = table.getItem(key(songId));
        if (observed == null) {
            return false;
        }
        final String currentState = observed.getState();
        final boolean fromPending = SongUploadState.PENDING_UPLOAD.name().equals(currentState);
        final boolean fromExpiredLease = SongUploadState.COMPLETING.name().equals(currentState)
                && observed.getCompletingLeaseUntil() != null
                && !observed.getCompletingLeaseUntil().isAfter(now);
        if (!fromPending && !fromExpiredLease) {
            return false;
        }

        // CAS guard on the exact lease we observed so two takeover racers cannot both win.
        final Expression guard = fromPending
                ? Expression.builder()
                        .expression("#st = :pending")
                        .expressionNames(Map.of("#st", "state"))
                        .expressionValues(Map.of(":pending", s(SongUploadState.PENDING_UPLOAD.name())))
                        .build()
                : Expression.builder()
                        .expression("#st = :completing AND #lu = :observed AND #lu <= :now")
                        .expressionNames(Map.of("#st", "state", "#lu", "completingLeaseUntil"))
                        .expressionValues(Map.of(
                                ":completing", s(SongUploadState.COMPLETING.name()),
                                ":observed", s(observed.getCompletingLeaseUntil().toString()),
                                ":now", s(now.toString())))
                        .build();

        observed.setState(SongUploadState.COMPLETING.name());
        observed.setCompletingLeaseUntil(newLeaseUntil);
        observed.setUpdatedAt(now);

        try {
            table.updateItem(UpdateItemEnhancedRequest.builder(SongUploadDocument.class)
                    .item(observed)
                    .conditionExpression(guard)
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public boolean markCompleted(final SongId songId, final Instant expectedLeaseUntil, final Instant at) {
        return conditionalTransition(songId, expectedLeaseUntil, SongUploadState.COMPLETED, at);
    }

    @Override
    public boolean releaseCompletingLease(final SongId songId, final Instant expectedLeaseUntil,
                                          final Instant at) {
        return conditionalTransition(songId, expectedLeaseUntil, SongUploadState.PENDING_UPLOAD, at);
    }

    @Override
    public boolean markAbortedFromPendingOrExpiredCompleting(final SongId songId, final Instant now) {
        try {
            table.updateItem(UpdateItemEnhancedRequest.builder(SongUploadDocument.class)
                    .item(observedWithState(songId, SongUploadState.ABORTED, now))
                    .conditionExpression(Expression.builder()
                            .expression("#st = :pending OR (#st = :completing AND #lu <= :now)")
                            .expressionNames(Map.of("#st", "state", "#lu", "completingLeaseUntil"))
                            .expressionValues(Map.of(
                                    ":pending", s(SongUploadState.PENDING_UPLOAD.name()),
                                    ":completing", s(SongUploadState.COMPLETING.name()),
                                    ":now", s(now.toString())))
                            .build())
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    private SongUploadDocument observedWithState(final SongId songId, final SongUploadState target,
                                                 final Instant now) {
        final SongUploadDocument observed = table.getItem(key(songId));
        if (observed == null) {
            throw new IllegalStateException("Upload record disappeared: " + songId.value());
        }
        observed.setState(target.name());
        observed.setCompletingLeaseUntil(null);
        observed.setUpdatedAt(now);
        return observed;
    }

    private boolean conditionalTransition(final SongId songId, final Instant expectedLeaseUntil,
                                         final SongUploadState target, final Instant at) {
        final SongUploadDocument observed = table.getItem(key(songId));
        if (observed == null || observed.getCompletingLeaseUntil() == null) {
            return false;
        }
        if (!observed.getCompletingLeaseUntil().equals(expectedLeaseUntil)) {
            return false;
        }
        observed.setState(target.name());
        if (target == SongUploadState.PENDING_UPLOAD) {
            observed.setCompletingLeaseUntil(null);
        }
        observed.setUpdatedAt(at);
        try {
            table.updateItem(UpdateItemEnhancedRequest.builder(SongUploadDocument.class)
                    .item(observed)
                    .conditionExpression(Expression.builder()
                            .expression("#st = :completing AND #lu = :expected")
                            .expressionNames(Map.of("#st", "state", "#lu", "completingLeaseUntil"))
                            .expressionValues(Map.of(
                                    ":completing", s(SongUploadState.COMPLETING.name()),
                                    ":expected", s(expectedLeaseUntil.toString())))
                            .build())
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public List<SongUpload> findExpiredByState(final SongUploadState state, final Instant expiryCutoff,
                                               final int limit) {
        final DynamoDbIndex<SongUploadDocument> index = table.index("state-expiry-index");
        final Page<SongUploadDocument> page = index.query(QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.sortBetween(
                        Key.builder()
                                .partitionValue(state.name())
                                .sortValue(0L)
                                .build(),
                        Key.builder()
                                .partitionValue(state.name())
                                .sortValue(expiryCutoff.getEpochSecond())
                                .build()))
                .limit(limit)
                .scanIndexForward(true)
                .build())
                .iterator().next();
        return page.items().stream().map(this::toDomain).toList();
    }

    private SongUploadDocument toDocument(final SongUpload upload) {
        final SongUploadDocument doc = new SongUploadDocument();
        doc.setSongId(upload.getSongId().value().toString());
        doc.setAlbumId(upload.getAlbumId().value().toString());
        doc.setArtistId(upload.getArtistId().value().toString());
        doc.setActorUserId(upload.getActorUserId().toString());
        doc.setContentType(upload.getContentType());
        doc.setContentLengthBytes(upload.getContentLengthBytes());
        doc.setStagingKey(upload.getStagingKey());
        doc.setFinalKey(upload.getFinalKey());
        doc.setMultipartUploadId(upload.getMultipartUploadId());
        doc.setState(upload.getState().name());
        doc.setCompletingLeaseUntil(upload.getCompletingLeaseUntil());
        doc.setCreatedAt(upload.getCreatedAt());
        doc.setUpdatedAt(upload.getUpdatedAt());
        doc.setExpiresAtEpochSeconds(upload.getExpiresAtEpochSeconds());
        return doc;
    }

    private SongUpload toDomain(final SongUploadDocument doc) {
        return SongUpload.rehydrate(
                new SongId(UUID.fromString(doc.getSongId())),
                new AlbumId(UUID.fromString(doc.getAlbumId())),
                new ArtistId(UUID.fromString(doc.getArtistId())),
                UUID.fromString(doc.getActorUserId()),
                doc.getContentType(),
                doc.getContentLengthBytes(),
                doc.getStagingKey(),
                doc.getFinalKey(),
                doc.getMultipartUploadId(),
                SongUploadState.valueOf(doc.getState()),
                doc.getCompletingLeaseUntil(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                doc.getExpiresAtEpochSeconds() == null ? 0L : doc.getExpiresAtEpochSeconds());
    }

    private static software.amazon.awssdk.services.dynamodb.model.AttributeValue s(final String value) {
        return software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
                .s(value)
                .build();
    }
}
