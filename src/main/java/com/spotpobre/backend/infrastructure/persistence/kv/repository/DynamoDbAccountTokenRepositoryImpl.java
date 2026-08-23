package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.AccountTokenDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DynamoDbAccountTokenRepositoryImpl implements DynamoDbAccountTokenRepository {

    private final DynamoDbTable<AccountTokenDocument> accountTokenTable;
    private final DynamoDbClient dynamoDbClient;

    @Override
    public void save(final AccountTokenDocument document) {
        accountTokenTable.putItem(document);
    }

    @Override
    public Optional<AccountTokenDocument> findByHashAndPurpose(final String tokenHash, final String purpose) {
        // "Active" excludes redeemed tokens: replays of a burned link answer like any invalid one.
        return Optional.ofNullable(accountTokenTable.getItem(Key.builder().partitionValue(tokenHash).build()))
                .filter(doc -> purpose.equals(doc.getPurpose()))
                .filter(doc -> doc.getUsedAtEpochSeconds() == null);
    }

    @Override
    public void markUsed(final String tokenHash, final long usedAtEpochSeconds) {
        try {
            // Conditional so a concurrent redemption of the same link loses instead of
            // double-spending the token.
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName("AccountTokens")
                    .key(Map.of("tokenHash", AttributeValue.builder().s(tokenHash).build()))
                    .conditionExpression("attribute_exists(tokenHash) AND attribute_not_exists(usedAtEpochSeconds)")
                    .updateExpression("SET usedAtEpochSeconds = :usedAt")
                    .expressionAttributeValues(Map.of(
                            ":usedAt", AttributeValue.builder().n(String.valueOf(usedAtEpochSeconds)).build()))
                    .build());
        } catch (ConditionalCheckFailedException e) {
            throw new IllegalStateException("Account token was already redeemed", e);
        }
    }
}
