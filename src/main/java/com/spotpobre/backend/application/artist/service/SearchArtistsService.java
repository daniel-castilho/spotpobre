package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.SearchArtistsUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.Normalization;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class SearchArtistsService implements SearchArtistsUseCase {

    public static final int MAX_QUERY_LENGTH = 100;

    private final ArtistRepository artistRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResult<Artist> searchArtists(final SearchArtistsCommand command) {
        if (command.pageRequest().pageSize() > PageRequest.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must not exceed " + PageRequest.MAX_PAGE_SIZE);
        }
        final String query = Normalization.trim(command.query());
        if (query == null || query.isEmpty() || query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("query must be between 1 and " + MAX_QUERY_LENGTH + " characters");
        }
        return artistRepository.searchByName(query, command.pageRequest(), command.cursor());
    }
}
