package com.spotpobre.backend.application.artist.port.in;

import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.user.model.UserId;

/**
 * Admin-only artist creation protected by the durable idempotency protocol. The actor (admin)
 * is part of the idempotency scope, so the same key from a different admin is a different
 * logical operation; authorization itself is re-checked by the route guard on every call.
 */
public interface CreateArtistIdempotentlyUseCase {

    CreateArtistOutcome createArtistIdempotently(final String rawIdempotencyKey,
                                                 final UserId actorUserId,
                                                 final CreateArtistCommand command);

    /**
     * @param artist   the created artist (either freshly created or recovered for replay)
     * @param replayed {@code true} when this outcome replays a previously completed execution
     */
    record CreateArtistOutcome(Artist artist, boolean replayed) {
    }

    record CreateArtistCommand(String name, java.util.UUID ownerUserId) {
    }
}
