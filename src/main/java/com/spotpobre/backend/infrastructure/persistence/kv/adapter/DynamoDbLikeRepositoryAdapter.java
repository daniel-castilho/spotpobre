package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.like.model.Like;
import com.spotpobre.backend.domain.like.port.LikeRepository;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.LikeDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.Select; // Corrected import

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DynamoDbLikeRepositoryAdapter implements LikeRepository {

    private final DynamoDbTable<LikeDocument> likesTable;

    private String toCompositeKey(String entityId, EntityType entityType) {
        return entityType.name() + "#" + entityId;
    }

    private String getEntityIdFromCompositeKey(String compositeKey) {
        return compositeKey.substring(compositeKey.indexOf('#') + 1);
    }

    private EntityType getEntityTypeFromCompositeKey(String compositeKey) {
        return EntityType.valueOf(compositeKey.substring(0, compositeKey.indexOf('#')));
    }

    @Override
    public void save(Like like) {
        LikeDocument doc = new LikeDocument();
        doc.setUserId(like.userId().value().toString());
        doc.setEntityCompositeKey(toCompositeKey(like.entityId(), like.entityType()));
        doc.setLikedAt(like.likedAt());
        likesTable.putItem(doc);
    }

    @Override
    public void delete(UserId userId, String entityId, EntityType entityType) {
        Key key = Key.builder()
                .partitionValue(userId.value().toString())
                .sortValue(toCompositeKey(entityId, entityType))
                .build();
        likesTable.deleteItem(key);
    }

    @Override
    public Optional<Like> findById(UserId userId, String entityId, EntityType entityType) {
        Key key = Key.builder()
                .partitionValue(userId.value().toString())
                .sortValue(toCompositeKey(entityId, entityType))
                .build();
        LikeDocument doc = likesTable.getItem(key);
        return Optional.ofNullable(doc).map(d -> new Like(
                new UserId(UUID.fromString(d.getUserId())),
                getEntityIdFromCompositeKey(d.getEntityCompositeKey()),
                getEntityTypeFromCompositeKey(d.getEntityCompositeKey()),
                d.getLikedAt()
        ));
    }

    @Override
    public long countLikesByEntity(String entityId, EntityType entityType) {
        DynamoDbIndex<LikeDocument> index = likesTable.index("entityId-index");
        String compositeKey = toCompositeKey(entityId, entityType);

        QueryEnhancedRequest query = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(compositeKey)))
                .select(Select.COUNT)
                .build();

        return index.query(query).iterator().next().count();
    }
}
