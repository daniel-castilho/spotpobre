package com.spotpobre.backend.infrastructure.web.dto.response;

import java.util.List;
import java.util.UUID;

public record PlaylistResponse(
        UUID id,
        String name,
        UUID ownerId,
        List<SongResponse> songs
) {
}
