package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserEmailDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DynamoDbUserRepositoryImpl implements DynamoDbUserRepository {

    private static final int MAX_REGISTER_ATTEMPTS = 5;

    private final DynamoDbTable<UserDocument> userTable;
    private final DynamoDbTable<UserEmailDocument> userEmailTable;
    private final DynamoDbIndex<UserDocument> emailIndex; // Will be configured in DynamoDbConfig
    private final DynamoDbClient dynamoDbClient;

    @Override
    public UserDocument save(final UserDocument userDocument) {
        userTable.putItem(userDocument);
        return userDocument;
    }

    @Override
    public boolean registerNew(final UserDocument userDocument) {
        final Map<String, AttributeValue> userItem = userTable.tableSchema().itemToMap(userDocument, true);
        final String email = userDocument.getProfile().getEmail();
        final Map<String, AttributeValue> emailItem = Map.of(
                "email", AttributeValue.builder().s(email).build()
        );
        for (int attempt = 0; attempt < MAX_REGISTER_ATTEMPTS; attempt++) {
            try {
                dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                        .transactItems(
                                TransactWriteItem.builder()
                                        .put(Put.builder()
                                                .tableName(userTable.tableName())
                                                .item(userItem)
                                                .conditionExpression("attribute_not_exists(id)")
                                                .build())
                                        .build(),
                                TransactWriteItem.builder()
                                        .put(Put.builder()
                                                .tableName(userEmailTable.tableName())
                                                .item(emailItem)
                                                .conditionExpression("attribute_not_exists(email)")
                                                .build())
                                        .build()
                        )
                        .build());
                return true;
            } catch (TransactionCanceledException e) {
                if (conditionalCheckFailed(e)) {
                    return false; // Email already registered (or id collision) — definitive.
                }
                // Transient TransactionConflict between concurrent registrations: retry.
            }
        }
        return false;
    }

    private static boolean conditionalCheckFailed(final TransactionCanceledException e) {
        return e.cancellationReasons() != null
                && e.cancellationReasons().stream()
                        .anyMatch(reason -> "ConditionalCheckFailed".equals(reason.code()));
    }

    @Override
    public Optional<UserDocument> findById(final UUID id) {
        return Optional.ofNullable(userTable.getItem(Key.builder().partitionValue(id.toString()).build()));
    }

    @Override
    public Optional<UserDocument> findByProfileEmail(final String email) {
        final QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(email).build());
        return emailIndex.query(queryConditional).stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }
}
