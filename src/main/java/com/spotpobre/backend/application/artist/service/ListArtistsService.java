package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.ListArtistsUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ListArtistsService implements ListArtistsUseCase {

    private final ArtistRepository artistRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResult<Artist> listArtists(final ListArtistsCommand command) {
        if (command.pageRequest().pageSize() > PageRequest.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must not exceed " + PageRequest.MAX_PAGE_SIZE);
        }
        return artistRepository.findAll(command.pageRequest(), command.cursor());
    }
}
