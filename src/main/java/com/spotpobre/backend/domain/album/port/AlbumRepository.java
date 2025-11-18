package com.spotpobre.backend.domain.album.port;

import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.artist.model.ArtistId;

import java.util.List;
import java.util.Optional;

public interface AlbumRepository {
    void save(Album album);
    Optional<Album> findById(AlbumId albumId);
    List<Album> findByArtistId(ArtistId artistId);
}
