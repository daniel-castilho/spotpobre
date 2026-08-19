package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.SearchArtistsUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class SearchArtistsService implements SearchArtistsUseCase {

    private final ArtistRepository artistRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResult<Artist> searchArtists(final SearchArtistsCommand command) {
        if (command.pageRequest().pageSize() > PageRequest.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must not exceed " + PageRequest.MAX_PAGE_SIZE);
        }
        return artistRepository.searchByName(command.query(), command.pageRequest(), command.cursor());
    }
}
