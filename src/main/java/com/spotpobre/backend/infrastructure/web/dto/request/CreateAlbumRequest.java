package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateAlbumRequest(
        @NotBlank String name,
        @NotNull UUID artistId,
        String coverArtUrl,
        List<String> songTitles
) {
}
