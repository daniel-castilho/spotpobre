package com.spotpobre.backend.application.artist.port.in;

import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;

public interface ListArtistsUseCase {

    PageResult<Artist> listArtists(final ListArtistsCommand command);

    record ListArtistsCommand(PageRequest pageRequest, String cursor) {
    }
}
