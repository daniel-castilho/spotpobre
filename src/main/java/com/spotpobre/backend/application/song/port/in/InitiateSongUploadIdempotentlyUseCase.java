package com.spotpobre.backend.application.song.port.in;

import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.SongUpload;

import java.util.UUID;

/**
 * Song upload initiation protected by the durable idempotency protocol (120 s lease). No Song
 * row is written here — the upload reserves the song identity in a staging record only, so a
 * pending upload is invisible to fetch/search/stream/like/playlist flows. Replays and crash
 * recoveries return a freshly presigned URL targeting the exact staging key bound to the
 * reserved song.
 */
public interface InitiateSongUploadIdempotentlyUseCase {

    InitiateUploadIdempotentResult initiateUploadIdempotently(final String rawIdempotencyKey,
                                                              final InitiateSongUploadCommand command);

    /**
     * @param upload   the staged upload record (fresh or recovered for replay)
     * @param presigned a presigned upload targeting {@code upload}'s staging key
     * @param replayed {@code true} when this outcome replays a previously completed execution
     */
    record InitiateUploadIdempotentResult(SongUpload upload, PresignedUploadResult presigned,
                                          boolean replayed) {
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
