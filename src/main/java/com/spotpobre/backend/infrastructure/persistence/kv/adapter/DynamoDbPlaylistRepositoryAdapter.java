package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.domain.common.ConflictException;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistConcurrentModificationException;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.PlaylistPersistenceMapper;
import com.spotpobre.backend.infrastructure.persistence.kv.repository.DynamoDbPlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DynamoDbPlaylistRepositoryAdapter implements PlaylistRepository {

    private static final String COUNTER_KEY_PREFIX = "OWNER_COUNT#";
    private static final String PLAYLISTS_TABLE = "Playlists";

    private final DynamoDbPlaylistRepository dynamoDbPlaylistRepository;
    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbTable<PlaylistDocument> playlistTable;
    private final PlaylistPersistenceMapper mapper;

    @Override
    public Optional<Playlist> findById(final PlaylistId id) {
        return dynamoDbPlaylistRepository.findById(id.value())
                .map(mapper::toDomain);
    }

    @Override
    public void createWithinOwnerLimit(final Playlist playlist, final int maxPlaylistsPerOwner) {
        final Map<String, AttributeValue> playlistItem =
                playlistTable.tableSchema().itemToMap(mapper.toDocument(playlist), false);
        final String counterId = COUNTER_KEY_PREFIX + playlist.getOwnerId().value();

        try {
            dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(
                            TransactWriteItem.builder().put(Put.builder()
                                    .tableName(PLAYLISTS_TABLE)
                                    .item(playlistItem)
                                    .conditionExpression("attribute_not_exists(id)")
                                    .build()).build(),
                            TransactWriteItem.builder().update(Update.builder()
                                    .tableName(PLAYLISTS_TABLE)
                                    .key(Map.of("id", string(counterId)))
                                    // Absent counter = owner created everything before this
                                    // mechanism existed; the transaction initializes it
                                    // (safe-side undercount, correctable by backfill).
                                    .conditionExpression(
                                            "attribute_not_exists(playlistCount) OR playlistCount < :max")
                                    .updateExpression(
                                            "SET playlistCount = if_not_exists(playlistCount, :zero) + :one")
                                    .expressionAttributeValues(Map.of(
                                            ":max", number(maxPlaylistsPerOwner),
                                            ":zero", number(0),
                                            ":one", number(1)))
                                    .build()).build())
                    .build());
        } catch (TransactionCanceledException e) {
            if (wasConditionalCheckFailure(e)) {
                throw new ConflictException(
                        "User cannot have more than " + maxPlaylistsPerOwner + " playlists.");
            }
            throw e;
        }
    }

    @Override
    public void update(final Playlist playlist) {
        final boolean updated = dynamoDbPlaylistRepository.update(mapper.toDocument(playlist));
        if (!updated) {
            throw new PlaylistConcurrentModificationException(playlist.getId());
        }
    }

    @Override
    public void deleteById(final PlaylistId id) {
        // Resolve the owner first so the removal and its counter decrement commit atomically.
        final PlaylistDocument document = dynamoDbPlaylistRepository.findById(id.value()).orElse(null);
        if (document == null) {
            return;
        }
        final String counterId = COUNTER_KEY_PREFIX + document.getOwnerId();

        dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder().delete(Delete.builder()
                                .tableName(PLAYLISTS_TABLE)
                                .key(Map.of("id", string(id.value().toString())))
                                .build()).build(),
                        TransactWriteItem.builder().update(Update.builder()
                                .tableName(PLAYLISTS_TABLE)
                                .key(Map.of("id", string(counterId)))
                                // No condition: an absent counter stays absent for owners whose
                                // playlists all predate the mechanism (if_not_exists seeds it at
                                // zero — undercount, the safe side). A present counter never goes
                                // negative in practice, because every successful delete consumed
                                // a creation that incremented it.
                                .updateExpression(
                                        "SET playlistCount = if_not_exists(playlistCount, :one) - :one")
                                .expressionAttributeValues(Map.of(":one", number(1)))
                                .build()).build())
                .build());
    }

    @Override
    public PageResult<Playlist> findByOwnerId(final UserId ownerId, final PageRequest pageRequest, final String exclusiveStartKey) {
        final PageResult<PlaylistDocument> documentPage = dynamoDbPlaylistRepository.findByOwnerId(ownerId.value(), pageRequest, exclusiveStartKey);
        return documentPage.map(mapper::toDomain);
    }

    private static boolean wasConditionalCheckFailure(final TransactionCanceledException e) {
        return e.cancellationReasons().stream()
                .anyMatch(reason -> "ConditionalCheckFailed".equals(reason.code()));
    }

    private static AttributeValue string(final String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue number(final long value) {
        return AttributeValue.builder().n(String.valueOf(value)).build();
    }
}
