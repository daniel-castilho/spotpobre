package com.spotpobre.backend.infrastructure.web.dto.response;

import java.util.UUID;

public record SongDetailsResponse(
        UUID id,
        String title,
        UUID albumId, // Changed from artistId to albumId
        String streamingUrl // This will be the pre-signed URL
) {
}
