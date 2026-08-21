package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlaylistRequest(
        @NotBlank(message = "Playlist name cannot be blank")
        @Size(max = 100, message = "Playlist name cannot exceed 100 characters")
        String name
) {
}
