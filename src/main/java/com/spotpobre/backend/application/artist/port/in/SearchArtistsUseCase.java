package com.spotpobre.backend.application.artist.port.in;

import com.spotpobre.backend.domain.artist.model.Artist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SearchArtistsUseCase {

    Page<Artist> searchArtists(final SearchArtistsCommand command);

    record SearchArtistsCommand(String query, Pageable pageable) {
    }
}
