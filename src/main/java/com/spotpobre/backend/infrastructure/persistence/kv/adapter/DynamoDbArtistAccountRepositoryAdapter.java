package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.model.ArtistPermission;
import com.spotpobre.backend.domain.artist.port.ArtistAccountRepository;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistAccountDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DynamoDbArtistAccountRepositoryAdapter implements ArtistAccountRepository {

    private final DynamoDbTable<ArtistAccountDocument> artistAccountsTable;

    @Override
    public void save(final ArtistAccount account) {
        artistAccountsTable.putItem(toDocument(account));
    }

    @Override
    public Optional<ArtistAccount> find(final ArtistId artistId, final UUID userId) {
        final ArtistAccountDocument item = artistAccountsTable.getItem(
                Key.builder()
                        .partitionValue(artistId.value().toString())
                        .sortValue(userId.toString())
                        .build());
        return Optional.ofNullable(item).map(this::toDomain);
    }

    @Override
    public List<ArtistAccount> findByArtistId(final ArtistId artistId) {
        return artistAccountsTable.query(
                        r -> r.queryConditional(QueryConditional.keyEqualTo(
                                k -> k.partitionValue(artistId.value().toString()))))
                .items().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean delete(final ArtistId artistId, final UUID userId) {
        final ArtistAccountDocument removed = artistAccountsTable.deleteItem(
                Key.builder()
                        .partitionValue(artistId.value().toString())
                        .sortValue(userId.toString())
                        .build());
        return removed != null;
    }

    private ArtistAccountDocument toDocument(final ArtistAccount account) {
        final ArtistAccountDocument document = new ArtistAccountDocument();
        document.setArtistId(account.artistId().value().toString());
        document.setUserId(account.userId().toString());
        document.setPermission(account.permission().name());
        document.setCreatedAt(account.createdAt());
        return document;
    }

    private ArtistAccount toDomain(final ArtistAccountDocument document) {
        return new ArtistAccount(
                ArtistId.from(document.getArtistId()),
                UUID.fromString(document.getUserId()),
                ArtistPermission.valueOf(document.getPermission()),
                document.getCreatedAt()
        );
    }
}
