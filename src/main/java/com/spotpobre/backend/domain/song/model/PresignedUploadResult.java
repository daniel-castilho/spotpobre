package com.spotpobre.backend.domain.song.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PresignedUploadResult(
        String storageKey,
        String multipartUploadId,
        Instant expiresAt,
        boolean multipart,
        List<PresignedUploadPart> parts
) {

    public PresignedUploadResult {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key cannot be blank.");
        }
        Objects.requireNonNull(expiresAt, "Expiration cannot be null.");
        parts = List.copyOf(parts == null ? List.of() : parts);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("At least one presigned part URL is required.");
        }
        if (multipart && (multipartUploadId == null || multipartUploadId.isBlank())) {
            throw new IllegalArgumentException("Multipart upload id is required for multipart uploads.");
        }
    }
}
