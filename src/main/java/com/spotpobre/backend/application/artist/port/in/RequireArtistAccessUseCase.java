package com.spotpobre.backend.application.artist.port.in;

import com.spotpobre.backend.domain.artist.model.ArtistId;

import java.util.UUID;

/**
 * Enforces the artist management policy: an actor may manage an artist's catalogue only
 * when they are an administrator or hold an OWNER/MANAGER membership on that artist.
 * Throws {@link com.spotpobre.backend.domain.common.ForbiddenException} otherwise.
 */
public interface RequireArtistAccessUseCase {

    void requireAccess(ActorArtistRef actor, ArtistId artistId);

    record ActorArtistRef(UUID userId, boolean isAdmin) {
    }
}
