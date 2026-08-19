package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.SongPersistenceMapper;
import com.spotpobre.backend.infrastructure.persistence.kv.repository.DynamoDbSongMetadataRepository;
import lombok.RequiredArgsConstructor;
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
    public PageResult<Song> findByAlbumId(final AlbumId albumId, final PageRequest pageRequest) {
        return dynamoDbSongMetadataRepository.findByAlbumId(albumId.value(), pageRequest)
                .map(mapper::toDomain);
    }

    @Override
    public PageResult<Song> searchByTitle(final String titleQuery, final PageRequest pageRequest, final String exclusiveStartKey) {
        return dynamoDbSongMetadataRepository.searchByTitle(titleQuery, pageRequest, exclusiveStartKey)
                .map(mapper::toDomain);
    }
}
