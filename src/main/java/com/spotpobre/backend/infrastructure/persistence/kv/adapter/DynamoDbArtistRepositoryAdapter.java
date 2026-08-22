package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistAccountDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.ArtistPersistenceMapper;
import com.spotpobre.backend.infrastructure.persistence.kv.repository.DynamoDbArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DynamoDbArtistRepositoryAdapter implements ArtistRepository {

    private final DynamoDbArtistRepository dynamoDbArtistRepository;
    private final ArtistPersistenceMapper mapper;
    private final DynamoDbTable<ArtistDocument> artistsTable;
    private final DynamoDbTable<ArtistAccountDocument> artistAccountsTable;
    private final DynamoDbEnhancedClient enhancedClient;

    @Override
    public Optional<Artist> findById(final ArtistId id) {
        return dynamoDbArtistRepository.findById(id.value())
                .map(mapper::toDomain);
    }

    @Override
    public void save(final Artist artist) {
        final ArtistDocument document = mapper.toDocument(artist);
        dynamoDbArtistRepository.save(document);
    }

    @Override
    public PageResult<Artist> searchByName(final String nameQuery, final PageRequest pageRequest, final String exclusiveStartKey) {
        return dynamoDbArtistRepository.searchByName(nameQuery, pageRequest, exclusiveStartKey)
                .map(mapper::toDomain);
    }

    @Override
    public void createWithOwner(final Artist artist, final ArtistAccount ownerAccount) {
        enhancedClient.transactWriteItems(TransactWriteItemsEnhancedRequest.builder()
                .addPutItem(artistsTable, TransactPutItemEnhancedRequest.builder(ArtistDocument.class)
                        .item(mapper.toDocument(artist))
                        .build())
                .addPutItem(artistAccountsTable, TransactPutItemEnhancedRequest.builder(ArtistAccountDocument.class)
                        .item(toAccountDocument(ownerAccount))
                        .build())
                .build());
    }

    private ArtistAccountDocument toAccountDocument(final ArtistAccount account) {
        final ArtistAccountDocument document = new ArtistAccountDocument();
        document.setArtistId(account.artistId().value().toString());
        document.setUserId(account.userId().toString());
        document.setPermission(account.permission().name());
        document.setCreatedAt(account.createdAt());
        return document;
    }
}
