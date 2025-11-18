package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.AlbumDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.AlbumPersistenceMapper;
import com.spotpobre.backend.infrastructure.persistence.kv.repository.DynamoDbAlbumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DynamoDbAlbumRepositoryAdapter implements AlbumRepository {

    private final DynamoDbAlbumRepository dynamoDbAlbumRepository;
    private final AlbumPersistenceMapper mapper;

    @Override
    public void save(Album album) {
        dynamoDbAlbumRepository.save(mapper.toDocument(album));
    }

    @Override
    public Optional<Album> findById(AlbumId albumId) {
        return dynamoDbAlbumRepository.findById(albumId.value()).map(mapper::toDomain);
    }

    @Override
    public List<Album> findByArtistId(ArtistId artistId) {
        return dynamoDbAlbumRepository.findByArtistId(artistId.value())
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
