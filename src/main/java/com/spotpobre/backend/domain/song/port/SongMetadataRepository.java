package com.spotpobre.backend.domain.song.port;

import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;

import java.util.Optional;

public interface SongMetadataRepository {
    Optional<Song> findById(final SongId id);
    void save(final Song song);
}
