package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.like.model.EntityType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ArtistLikeStrategy implements LikeStrategy {

    private final ArtistRepository artistRepository;

    @Override
    public boolean supports(EntityType entityType) {
        return EntityType.ARTIST.equals(entityType);
    }

    @Override
    public void validateEntityExists(String entityId) {
        artistRepository.findById(new ArtistId(UUID.fromString(entityId)))
                .orElseThrow(() -> new IllegalArgumentException("Artist not found: " + entityId));
    }
}
