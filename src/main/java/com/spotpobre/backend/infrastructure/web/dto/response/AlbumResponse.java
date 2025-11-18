package com.spotpobre.backend.infrastructure.web.dto.response;

import java.util.List;
import java.util.UUID;

public record AlbumResponse(
        UUID id,
        UUID artistId,
        String name,
        String coverArtUrl,
        List<SongResponse> songs
) {
}
