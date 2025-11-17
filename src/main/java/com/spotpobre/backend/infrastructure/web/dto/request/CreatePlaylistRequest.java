package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreatePlaylistRequest(
        @NotBlank(message = "Playlist name cannot be blank")
        String name
) {
}
