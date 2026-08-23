package com.spotpobre.backend.application.playlist.port.in;

import com.spotpobre.backend.domain.playlist.model.Playlist;

import java.util.UUID;

/**
 * Playlist creation protected by the durable idempotency protocol. The authenticated actor is
 * both the scope and the playlist owner; the user-existence check runs before the claim on
 * every call, while the per-user playlist limit is enforced at execution time (state-dependent).
 */
public interface CreatePlaylistIdempotentlyUseCase {

    CreatePlaylistOutcome createPlaylistIdempotently(final String rawIdempotencyKey,
                                                     final UUID actorUserId,
                                                     final String name);

    /**
     * @param playlist the created playlist (either freshly created or recovered for replay)
     * @param replayed {@code true} when this outcome replays a previously completed execution
     */
    record CreatePlaylistOutcome(Playlist playlist, boolean replayed) {
    }
}
