package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SongLikeStrategy implements LikeStrategy {

    private final SongMetadataRepository songMetadataRepository;

    @Override
    public boolean supports(EntityType entityType) {
        return EntityType.SONG.equals(entityType);
    }

    @Override
    public void validateEntityExists(String entityId) {
        songMetadataRepository.findById(new SongId(UUID.fromString(entityId)))
               .orElseThrow(() -> new IllegalArgumentException("Song not found: " + entityId));
    }
}
