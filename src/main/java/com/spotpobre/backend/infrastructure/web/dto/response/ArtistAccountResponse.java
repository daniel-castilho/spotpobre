package com.spotpobre.backend.infrastructure.web.dto.response;

import com.spotpobre.backend.domain.artist.model.ArtistPermission;

import java.time.Instant;
import java.util.UUID;

public record ArtistAccountResponse(
        UUID artistId,
        UUID userId,
        ArtistPermission permission,
        Instant createdAt
) {
}
