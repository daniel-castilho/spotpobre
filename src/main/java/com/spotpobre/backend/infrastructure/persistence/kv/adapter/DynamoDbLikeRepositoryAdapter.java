package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.like.model.Like;
import com.spotpobre.backend.domain.like.port.LikeRepository;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.LikeDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DynamoDbLikeRepositoryAdapter implements LikeRepository {

    private final DynamoDbTable<LikeDocument> likesTable;

    private String toCompositeKey(String entityId, EntityType entityType) {
        return entityType.name() + "#" + entityId;
    }

    private Key keyFor(UserId userId, String entityId, EntityType entityType) {
        return Key.builder()
                .partitionValue(userId.value().toString())
                .sortValue(toCompositeKey(entityId, entityType))
                .build();
    }

    @Override
    public boolean createIfAbsent(Like like) {
        LikeDocument doc = new LikeDocument();
        doc.setUserId(like.userId().value().toString());
        doc.setEntityCompositeKey(toCompositeKey(like.entityId(), like.entityType()));
        doc.setLikedAt(like.likedAt());
        try {
            likesTable.putItem(PutItemEnhancedRequest.builder(LikeDocument.class)
                    .item(doc)
                    .conditionExpression(Expression.builder()
                            .expression("attribute_not_exists(userId)")
                            .build())
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public boolean deleteIfPresent(UserId userId, String entityId, EntityType entityType) {
        try {
            likesTable.deleteItem(DeleteItemEnhancedRequest.builder()
                    .key(keyFor(userId, entityId, entityType))
                    .conditionExpression(Expression.builder()
                            .expression("attribute_exists(userId)")
                            .build())
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }
}
