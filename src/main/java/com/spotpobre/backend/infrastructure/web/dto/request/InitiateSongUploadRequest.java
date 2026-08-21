package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InitiateSongUploadRequest(
        @NotBlank(message = "Song title cannot be blank")
        @Size(max = 200, message = "Song title cannot exceed 200 characters")
        String title,

        @NotBlank(message = "Content type cannot be blank")
        String contentType,

        @NotNull(message = "Content length cannot be null")
        @Positive(message = "Content length must be greater than zero")
        Long contentLengthBytes
) {
}
