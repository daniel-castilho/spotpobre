package com.spotpobre.backend.application.album.port.in;

import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import java.util.List;

public interface CreateAlbumUseCase {
    Album createAlbum(CreateAlbumCommand command);

    record CreateAlbumCommand(
            String name,
            ArtistId artistId,
            String coverArtUrl,
            List<String> songTitles
    ) {
    }
}
