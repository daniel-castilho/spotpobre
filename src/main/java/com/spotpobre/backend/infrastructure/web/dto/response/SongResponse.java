package com.spotpobre.backend.infrastructure.web.dto.response;

import java.util.UUID;

public record SongResponse(
        UUID id,
        String title,
        UUID artistId
) {
}
