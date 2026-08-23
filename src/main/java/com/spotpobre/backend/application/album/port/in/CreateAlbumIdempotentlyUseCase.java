package com.spotpobre.backend.application.album.port.in;

import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.artist.model.ArtistId;

import java.util.UUID;

/**
 * Album creation protected by the durable idempotency protocol. The authenticated actor scopes
 * the claim; the artist-membership authorization is re-checked before the claim on every call,
 * so replays are only served to callers who may still see the outcome.
 */
public interface CreateAlbumIdempotentlyUseCase {

    CreateAlbumOutcome createAlbumIdempotently(final String rawIdempotencyKey,
                                               final CreateAlbumCommand command);

    /**
     * @param album    the created album (either freshly created or recovered for replay)
     * @param replayed {@code true} when this outcome replays a previously completed execution
     */
    record CreateAlbumOutcome(Album album, boolean replayed) {
    }

    record CreateAlbumCommand(String name, ArtistId artistId, String coverArtUrl,
                              UUID actorUserId, boolean actorIsAdmin) {
    }
}
