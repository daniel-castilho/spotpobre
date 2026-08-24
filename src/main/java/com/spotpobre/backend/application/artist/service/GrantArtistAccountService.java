package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.GrantArtistAccountUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.port.ArtistAccountRepository;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Instant;

@RequiredArgsConstructor
public class GrantArtistAccountService implements GrantArtistAccountUseCase {

    private final ArtistRepository artistRepository;
    private final ArtistAccountRepository artistAccountRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Override
    public ArtistAccount grant(final GrantArtistAccountCommand command) {
        if (!command.actorIsAdmin()) {
            throw new ForbiddenException("Only administrators can manage artist accounts");
        }
        final ArtistId artistId = command.artistId();
        artistRepository.findById(artistId)
                .orElseThrow(() -> new NotFoundException("Artist not found: " + artistId));
        // Fail closed: a membership row pointing at a nonexistent account would silently
        // grant nothing today but become an ownership claim once the user signs up.
        userRepository.findById(UserId.from(command.targetUserId().toString()))
                .orElseThrow(() -> new NotFoundException(
                        "Target user not found: " + command.targetUserId()));

        final ArtistAccount account = new ArtistAccount(
                artistId, command.targetUserId(), command.permission(), clock.instant());
        artistAccountRepository.save(account);
        return account;
    }
}
