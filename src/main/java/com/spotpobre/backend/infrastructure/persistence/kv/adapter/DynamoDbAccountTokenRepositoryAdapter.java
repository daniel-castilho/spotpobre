package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.domain.user.model.AccountToken;
import com.spotpobre.backend.domain.user.model.AccountTokenPurpose;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.port.AccountTokenRepository;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.AccountTokenDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.repository.DynamoDbAccountTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DynamoDbAccountTokenRepositoryAdapter implements AccountTokenRepository {

    private final DynamoDbAccountTokenRepository dynamoDbAccountTokenRepository;

    @Override
    public void save(final AccountToken token) {
        final AccountTokenDocument doc = new AccountTokenDocument();
        doc.setTokenHash(token.tokenHash());
        doc.setUserId(token.userId().value().toString());
        doc.setPurpose(token.purpose().name());
        doc.setExpiresAtEpochSeconds(token.expiresAt().getEpochSecond());
        doc.setCreatedAt(Instant.now());
        dynamoDbAccountTokenRepository.save(doc);
    }

    @Override
    public Optional<AccountToken> findActiveByHash(final String tokenHash, final AccountTokenPurpose purpose) {
        return dynamoDbAccountTokenRepository.findByHashAndPurpose(tokenHash, purpose.name())
                .map(doc -> new AccountToken(
                        new UserId(UUID.fromString(doc.getUserId())),
                        purpose,
                        doc.getTokenHash(),
                        Instant.ofEpochSecond(doc.getExpiresAtEpochSeconds())));
    }

    @Override
    public void markUsed(final String tokenHash) {
        dynamoDbAccountTokenRepository.markUsed(tokenHash, Instant.now().getEpochSecond());
    }
}
