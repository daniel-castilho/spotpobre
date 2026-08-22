package com.spotpobre.backend.application.artist.port.in;

import com.spotpobre.backend.domain.artist.model.ArtistId;

import java.util.UUID;

/**
 * Removes a membership from an artist. Admin-only operation.
 */
public interface RevokeArtistAccountUseCase {

    void revoke(RevokeArtistAccountCommand command);

    record RevokeArtistAccountCommand(
            boolean actorIsAdmin,
            ArtistId artistId,
            UUID targetUserId
    ) {
    }
}
