package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.RequireArtistAccessUseCase;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.port.ArtistAccountRepository;
import com.spotpobre.backend.domain.common.ForbiddenException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ArtistAccessService implements RequireArtistAccessUseCase {

    private final ArtistAccountRepository artistAccountRepository;

    @Override
    public void requireAccess(final ActorArtistRef actor, final ArtistId artistId) {
        if (actor.isAdmin()) {
            return;
        }
        artistAccountRepository.find(artistId, actor.userId())
                .orElseThrow(() -> new ForbiddenException(
                        "User " + actor.userId() + " has no membership on artist " + artistId));
    }
}
