package com.spotpobre.backend.application.song.port.in;

import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.Song;

import java.util.UUID;

/**
 * Song upload initiation protected by the durable idempotency protocol (120 s lease). Replays
 * and crash recoveries return a freshly presigned URL targeting the exact storage key bound to
 * the reserved song, so a client that lost its connection can resume with a new key-less retry.
 */
public interface InitiateSongUploadIdempotentlyUseCase {

    InitiateUploadIdempotentResult initiateUploadIdempotently(final String rawIdempotencyKey,
                                                              final InitiateSongUploadCommand command);

    /**
     * @param song     the initiated song (freshly created or recovered for replay)
     * @param upload   a presigned upload targeting {@code song}'s storage key
     * @param replayed {@code true} when this outcome replays a previously completed execution
     */
    record InitiateUploadIdempotentResult(Song song, PresignedUploadResult upload, boolean replayed) {
    }

    record InitiateSongUploadCommand(
            String title,
            AlbumId albumId,
            String contentType,
            long contentLengthBytes,
            UUID actorUserId,
            boolean actorIsAdmin
    ) {
    }
}
