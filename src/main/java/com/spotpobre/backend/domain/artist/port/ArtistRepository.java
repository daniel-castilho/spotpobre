package com.spotpobre.backend.domain.artist.port;

import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ArtistRepository {
    Optional<Artist> findById(final ArtistId id);
    void save(final Artist artist);
    Page<Artist> searchByName(final String nameQuery, final Pageable pageable);
}
