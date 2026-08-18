package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InitiateSongUploadRequest(
        @NotBlank(message = "Song title cannot be blank")
        String title,

        @NotBlank(message = "Content type cannot be blank")
        String contentType,

        @NotNull(message = "Content length cannot be null")
        @Positive(message = "Content length must be greater than zero")
        Long contentLengthBytes
) {
}
