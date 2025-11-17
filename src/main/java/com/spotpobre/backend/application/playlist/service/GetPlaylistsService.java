package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.GetPlaylistsUseCase;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.infrastructure.persistence.kv.model.DynamoDbPage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class GetPlaylistsService implements GetPlaylistsUseCase {

    private final PlaylistRepository playlistRepository;

    @Override
    @Transactional(readOnly = true)
    public DynamoDbPage<Playlist> getPlaylists(final Pageable pageable, final String exclusiveStartKey) {
        return playlistRepository.findAll(pageable, exclusiveStartKey);
    }
}
