package com.spotpobre.backend.infrastructure.web.dto.request;

import com.spotpobre.backend.domain.artist.model.ArtistPermission;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GrantArtistAccountRequest(
        @NotNull(message = "User id cannot be null")
        UUID userId,

        @NotNull(message = "Permission cannot be null")
        ArtistPermission permission
) {
}
