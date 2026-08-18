package com.spotpobre.backend.infrastructure.web.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InitiateSongUploadResponse(
        UUID songId,
        String title,
        UUID albumId,
        String storageKey,
        String multipartUploadId,
        Instant expiresAt,
        boolean multipart,
        List<PresignedUploadPartResponse> parts
) {
}
