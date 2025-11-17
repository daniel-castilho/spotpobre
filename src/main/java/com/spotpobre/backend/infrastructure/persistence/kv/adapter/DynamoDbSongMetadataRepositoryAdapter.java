package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.SongPersistenceMapper;
import com.spotpobre.backend.infrastructure.persistence.kv.repository.DynamoDbSongMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DynamoDbSongMetadataRepositoryAdapter implements SongMetadataRepository {

    private final DynamoDbSongMetadataRepository dynamoDbSongMetadataRepository;
    private final SongPersistenceMapper mapper;

    @Override
    public Optional<Song> findById(final SongId id) {
        return dynamoDbSongMetadataRepository.findById(id.value())
                .map(mapper::toDomain);
    }

    @Override
    public void save(final Song song) {
        final SongDocument document = mapper.toDocument(song);
        dynamoDbSongMetadataRepository.save(document);
    }

    @Override
    public Page<Song> searchByTitle(final String titleQuery, final Pageable pageable) {
        Page<SongDocument> documentPage = dynamoDbSongMetadataRepository.searchByTitle(titleQuery, pageable);
        return documentPage.map(mapper::toDomain);
    }
}
