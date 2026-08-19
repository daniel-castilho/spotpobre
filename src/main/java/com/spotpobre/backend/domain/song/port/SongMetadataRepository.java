package com.spotpobre.backend.domain.song.port;

import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;

import java.util.Optional;

public interface SongMetadataRepository {
    Optional<Song> findById(final SongId id);
    void save(final Song song);
    PageResult<Song> findByAlbumId(final AlbumId albumId, final PageRequest pageRequest);
    PageResult<Song> searchByTitle(final String titleQuery, final PageRequest pageRequest);
}
