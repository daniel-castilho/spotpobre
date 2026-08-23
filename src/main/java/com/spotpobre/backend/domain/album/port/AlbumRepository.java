package com.spotpobre.backend.domain.album.port;

import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;

import java.util.Optional;

public interface AlbumRepository {
    void save(Album album);
    Optional<Album> findById(AlbumId albumId);

    /**
     * Cursor-paginated albums of one artist via the {@code artistId-index} GSI.
     */
    PageResult<Album> findByArtistId(ArtistId artistId, PageRequest pageRequest, String exclusiveStartKey);
}
