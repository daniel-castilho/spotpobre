package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DynamoDbUserRepositoryImpl implements DynamoDbUserRepository {

    private final DynamoDbTable<UserDocument> userTable;
    private final DynamoDbIndex<UserDocument> emailIndex; // Will be configured in DynamoDbConfig

    @Override
    public UserDocument save(final UserDocument userDocument) {
        userTable.putItem(userDocument);
        return userDocument;
    }

    @Override
    public Optional<UserDocument> findById(final UUID id) {
        return Optional.ofNullable(userTable.getItem(Key.builder().partitionValue(id.toString()).build()));
    }

    @Override
    public Optional<UserDocument> findByProfileEmail(final String email) {
        final QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(email).build());
        return emailIndex.query(queryConditional).stream().findFirst().map(page -> page.items().get(0));
    }
}
