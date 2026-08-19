package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.ArtistPersistenceMapper;
import com.spotpobre.backend.infrastructure.persistence.kv.repository.DynamoDbArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DynamoDbArtistRepositoryAdapter implements ArtistRepository {

    private final DynamoDbArtistRepository dynamoDbArtistRepository;
    private final ArtistPersistenceMapper mapper;

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
}
