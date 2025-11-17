package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.CreateArtistUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class CreateArtistService implements CreateArtistUseCase {

    private final ArtistRepository artistRepository;

    @Override
    @Transactional
    public Artist createArtist(final CreateArtistCommand command) {
        final Artist artist = Artist.create(command.name());
        artistRepository.save(artist);
        return artist;
    }
}
