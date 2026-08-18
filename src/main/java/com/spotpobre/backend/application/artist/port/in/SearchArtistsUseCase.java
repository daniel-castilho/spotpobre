package com.spotpobre.backend.application.artist.port.in;

import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;

public interface SearchArtistsUseCase {

    PageResult<Artist> searchArtists(final SearchArtistsCommand command);

    record SearchArtistsCommand(String query, PageRequest pageRequest) {
    }
}
