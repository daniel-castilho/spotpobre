package com.spotpobre.backend.domain.song.model;

public record CompletedUploadPart(int partNumber, String eTag) {

    public CompletedUploadPart {
        if (partNumber < 1) {
            throw new IllegalArgumentException("Part number must be at least 1.");
        }
        if (eTag == null || eTag.isBlank()) {
            throw new IllegalArgumentException("Part ETag cannot be blank.");
        }
    }
}
