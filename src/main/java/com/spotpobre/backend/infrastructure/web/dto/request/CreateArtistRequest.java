package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateArtistRequest(
        @NotBlank(message = "Artist name cannot be blank")
        String name
) {
}
