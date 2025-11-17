package com.spotpobre.backend.infrastructure.web.dto.response;

import java.util.UUID;

public record SongDetailsResponse(
        UUID id,
        String title,
        UUID artistId,
        String streamingUrl // This will be the pre-signed URL
) {
}
