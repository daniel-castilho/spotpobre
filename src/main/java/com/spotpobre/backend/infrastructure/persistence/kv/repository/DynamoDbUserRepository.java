package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserDocument;

import java.util.Optional;
import java.util.UUID;

public interface DynamoDbUserRepository {
    UserDocument save(final UserDocument userDocument);
    Optional<UserDocument> findById(final UUID id);
    Optional<UserDocument> findByProfileEmail(final String email);
}
