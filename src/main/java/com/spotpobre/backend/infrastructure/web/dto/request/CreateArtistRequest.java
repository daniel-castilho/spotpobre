package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateArtistRequest(
        @NotBlank(message = "Artist name cannot be blank")
        @Size(max = 200, message = "Artist name cannot exceed 200 characters")
        String name
) {
}
