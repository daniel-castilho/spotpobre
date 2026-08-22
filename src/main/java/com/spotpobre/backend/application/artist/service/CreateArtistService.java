package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.CreateArtistUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@RequiredArgsConstructor
public class CreateArtistService implements CreateArtistUseCase {

    private final ArtistRepository artistRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Artist createArtist(final CreateArtistCommand command) {
        Objects.requireNonNull(command.ownerUserId(), "ownerUserId is required");
        userRepository.findById(new UserId(command.ownerUserId()))
                .orElseThrow(() -> new NotFoundException(
                        "Owner user not found: " + command.ownerUserId()));

        final Artist artist = Artist.create(command.name());
        final ArtistAccount ownerAccount = ArtistAccount.owner(
                artist.getId(), command.ownerUserId(), java.time.Instant.now());
        artistRepository.createWithOwner(artist, ownerAccount);
        return artist;
    }
}
