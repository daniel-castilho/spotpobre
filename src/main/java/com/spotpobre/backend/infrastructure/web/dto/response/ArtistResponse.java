package com.spotpobre.backend.infrastructure.web.dto.response;

import java.util.List;
import java.util.UUID;

public record ArtistResponse(
        UUID id,
        String name,
        List<SongResponse> songs // Reusing SongResponse
) {
}
