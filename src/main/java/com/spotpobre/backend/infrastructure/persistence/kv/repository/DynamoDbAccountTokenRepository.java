package com.spotpobre.backend.infrastructure.persistence.kv.repository;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.AccountTokenDocument;

import java.util.Optional;

public interface DynamoDbAccountTokenRepository {

    void save(final AccountTokenDocument document);

    Optional<AccountTokenDocument> findByHashAndPurpose(final String tokenHash, final String purpose);

    void markUsed(final String tokenHash, final long usedAtEpochSeconds);
}
