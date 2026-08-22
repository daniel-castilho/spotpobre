package com.spotpobre.backend.application.artist.port.in;

import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.model.ArtistPermission;

import java.util.UUID;

/**
 * Grants a membership on an artist. Admin-only operation.
 */
public interface GrantArtistAccountUseCase {

    ArtistAccount grant(GrantArtistAccountCommand command);

    record GrantArtistAccountCommand(
            boolean actorIsAdmin,
            ArtistId artistId,
            UUID targetUserId,
            ArtistPermission permission
    ) {
    }
}
