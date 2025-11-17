package com.spotpobre.backend.infrastructure.persistence.kv.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

@DynamoDbBean
public class LikeDocument {

    private String userId;
    private String entityCompositeKey;
    private Instant likedAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("userId")
    @DynamoDbSecondarySortKey(indexNames = "entityId-index")
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("entityCompositeKey")
    @DynamoDbSecondaryPartitionKey(indexNames = "entityId-index")
    public String getEntityCompositeKey() {
        return entityCompositeKey;
    }

    public void setEntityCompositeKey(String entityCompositeKey) {
        this.entityCompositeKey = entityCompositeKey;
    }

    public Instant getLikedAt() {
        return likedAt;
    }

    public void setLikedAt(Instant likedAt) {
        this.likedAt = likedAt;
    }
}
