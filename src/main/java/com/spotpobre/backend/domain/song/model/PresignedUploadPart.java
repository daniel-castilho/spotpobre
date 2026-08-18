package com.spotpobre.backend.domain.song.model;

public record PresignedUploadPart(int partNumber, String url) {

    public PresignedUploadPart {
        if (partNumber < 1) {
            throw new IllegalArgumentException("Part number must be at least 1.");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Presigned part URL cannot be blank.");
        }
    }
}
