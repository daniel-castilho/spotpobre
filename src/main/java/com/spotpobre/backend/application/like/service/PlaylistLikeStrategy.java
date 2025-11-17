package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlaylistLikeStrategy implements LikeStrategy {

    private final PlaylistRepository playlistRepository;

    @Override
    public boolean supports(EntityType entityType) {
        return EntityType.PLAYLIST.equals(entityType);
    }

    @Override
    public void validateEntityExists(String entityId) {
        playlistRepository.findById(new PlaylistId(UUID.fromString(entityId)))
                .orElseThrow(() -> new IllegalArgumentException("Playlist not found: " + entityId));
    }
}
