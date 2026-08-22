package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.GrantArtistAccountUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.port.ArtistAccountRepository;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.common.NotFoundException;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

@RequiredArgsConstructor
public class GrantArtistAccountService implements GrantArtistAccountUseCase {

    private final ArtistRepository artistRepository;
    private final ArtistAccountRepository artistAccountRepository;

    @Override
    public ArtistAccount grant(final GrantArtistAccountCommand command) {
        if (!command.actorIsAdmin()) {
            throw new ForbiddenException("Only administrators can manage artist accounts");
        }
        final ArtistId artistId = command.artistId();
        artistRepository.findById(artistId)
                .orElseThrow(() -> new NotFoundException("Artist not found: " + artistId));

        final ArtistAccount account = new ArtistAccount(
                artistId, command.targetUserId(), command.permission(), Instant.now());
        artistAccountRepository.save(account);
        return account;
    }
}
