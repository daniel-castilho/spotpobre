package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateAlbumRequest(
        @NotBlank(message = "Album name cannot be blank")
        @Size(max = 200, message = "Album name cannot exceed 200 characters")
        String name,
        @NotNull UUID artistId,
        @Size(max = 2048, message = "Cover art URL cannot exceed 2048 characters")
        String coverArtUrl
) {
}
