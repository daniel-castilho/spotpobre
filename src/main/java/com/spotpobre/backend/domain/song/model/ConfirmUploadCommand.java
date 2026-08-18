package com.spotpobre.backend.domain.song.model;

import java.util.List;

public record ConfirmUploadCommand(
        String storageKey,
        String multipartUploadId,
        List<CompletedUploadPart> completedParts
) {

    public ConfirmUploadCommand {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key cannot be blank.");
        }
        completedParts = List.copyOf(completedParts == null ? List.of() : completedParts);
    }

    public boolean isMultipart() {
        return multipartUploadId != null && !multipartUploadId.isBlank();
    }
}
