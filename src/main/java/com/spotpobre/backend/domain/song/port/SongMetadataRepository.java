package com.spotpobre.backend.domain.song.port;

import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface SongMetadataRepository {
    Optional<Song> findById(final SongId id);
    void save(final Song song);
    Page<Song> searchByTitle(final String titleQuery, final Pageable pageable);
}
