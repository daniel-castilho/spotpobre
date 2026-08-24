package com.spotpobre.backend.domain.song.model;

/**
 * Server-observed metadata of a stored object, used for upload integrity verification
 * (declared vs actual content type and size). Pure Java.
 */
public record StorageObjectHead(String contentType, long contentLengthBytes) {

    public StorageObjectHead {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType is required");
        }
        if (contentLengthBytes < 0) {
            throw new IllegalArgumentException("contentLengthBytes must not be negative");
        }
    }
}
