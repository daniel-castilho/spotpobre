package com.spotpobre.backend.application.album.port.in;

import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;

public interface ListAlbumsByArtistUseCase {

    /**
     * @return the cursor-paginated albums of the artist.
     * @throws com.spotpobre.backend.domain.common.NotFoundException if the artist does not exist.
     */
    PageResult<Album> listAlbumsByArtist(final ListAlbumsByArtistCommand command);

    record ListAlbumsByArtistCommand(ArtistId artistId, PageRequest pageRequest, String cursor) {
    }
}
