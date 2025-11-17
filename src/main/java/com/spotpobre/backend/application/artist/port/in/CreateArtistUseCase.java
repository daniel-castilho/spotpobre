package com.spotpobre.backend.application.artist.port.in;

import com.spotpobre.backend.domain.artist.model.Artist;

public interface CreateArtistUseCase {

    Artist createArtist(final CreateArtistCommand command);

    record CreateArtistCommand(String name) {
    }
}
