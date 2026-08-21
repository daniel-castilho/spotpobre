package com.spotpobre.backend.domain.song.model;

import com.spotpobre.backend.domain.common.PayloadTooLargeException;

import java.util.Locale;
import java.util.Set;

/**
 * Authorization input for a direct-to-storage song upload. Validates content type and size
 * before a presigned URL is issued; never carries file bytes.
 */
public record SongUploadCommand(String contentType, long contentLengthBytes) {

    public static final long MAX_CONTENT_LENGTH_BYTES = 500L * 1024 * 1024;
    public static final long MULTIPART_THRESHOLD_BYTES = 100L * 1024 * 1024;
    public static final long MULTIPART_PART_SIZE_BYTES = 50L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "audio/mpeg",
            "audio/mp3",
            "audio/wav",
            "audio/x-wav",
            "audio/flac",
            "audio/aac",
            "audio/ogg",
            "audio/mp4",
            "audio/x-m4a"
    );

    public SongUploadCommand {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content type cannot be blank.");
        }
        final String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported audio content type: " + contentType);
        }
        if (contentLengthBytes <= 0) {
            throw new IllegalArgumentException("Content length must be greater than zero.");
        }
        if (contentLengthBytes > MAX_CONTENT_LENGTH_BYTES) {
            throw new PayloadTooLargeException("Declared audio size exceeds the maximum allowed size of 500 MB.");
        }
        contentType = normalized;
    }

    public boolean requiresMultipart() {
        return contentLengthBytes > MULTIPART_THRESHOLD_BYTES;
    }

    public int partCount() {
        if (!requiresMultipart()) {
            return 1;
        }
        return (int) Math.ceil((double) contentLengthBytes / MULTIPART_PART_SIZE_BYTES);
    }
}
