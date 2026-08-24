package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.AccountTokenDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
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
    public int burnAllForUser(final String userId, final String purpose, final long usedAtEpochSeconds) {
        // Bounded GSI lookup (never a table scan); each burn is conditional on the used flag.
        var pages = accountTokenTable.index("userId-index").query(r -> r
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(userId).build()))
                .filterExpression(Expression.builder()
                        .expression("#p = :purpose AND attribute_not_exists(usedAtEpochSeconds)")
                        .expressionNames(Map.of("#p", "purpose"))
                        .expressionValues(Map.of(":purpose",
                                AttributeValue.builder().s(purpose).build()))
                        .build()));
        int burned = 0;
        for (var page : pages) {
            for (AccountTokenDocument doc : page.items()) {
                try {
                    dynamoDbClient.updateItem(UpdateItemRequest.builder()
                            .tableName("AccountTokens")
                            .key(Map.of("tokenHash",
                                    AttributeValue.builder().s(doc.getTokenHash()).build()))
                            .conditionExpression("attribute_not_exists(usedAtEpochSeconds)")
                            .updateExpression("SET usedAtEpochSeconds = :usedAt")
                            .expressionAttributeValues(Map.of(":usedAt",
                                    AttributeValue.builder().n(String.valueOf(usedAtEpochSeconds)).build()))
                            .build());
                    burned++;
                } catch (ConditionalCheckFailedException e) {
                    // Already redeemed concurrently — nothing to do.
                }
            }
        }
        return burned;
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
