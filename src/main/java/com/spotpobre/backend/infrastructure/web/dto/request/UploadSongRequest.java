package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UploadSongRequest(
        @NotBlank(message = "Song title cannot be blank")
        String title,

        @NotNull(message = "Album ID cannot be null")
        UUID albumId // Changed from artistId to albumId
) {
}
