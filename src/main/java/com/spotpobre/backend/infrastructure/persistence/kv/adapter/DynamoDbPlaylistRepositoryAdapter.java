package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.PlaylistPersistenceMapper;
import com.spotpobre.backend.infrastructure.persistence.kv.repository.DynamoDbPlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DynamoDbPlaylistRepositoryAdapter implements PlaylistRepository {

    private final DynamoDbPlaylistRepository dynamoDbPlaylistRepository;
    private final PlaylistPersistenceMapper mapper;

    @Override
    public Optional<Playlist> findById(final PlaylistId id) {
        return dynamoDbPlaylistRepository.findById(id.value())
                .map(mapper::toDomain);
    }

    @Override
    public void save(final Playlist playlist) {
        final PlaylistDocument document = mapper.toDocument(playlist);
        dynamoDbPlaylistRepository.save(document);
    }
}
