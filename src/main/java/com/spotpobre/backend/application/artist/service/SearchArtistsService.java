package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.SearchArtistsUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class SearchArtistsService implements SearchArtistsUseCase {

    private final ArtistRepository artistRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<Artist> searchArtists(final SearchArtistsCommand command) {
        return artistRepository.searchByName(command.query(), command.pageable());
    }
}
