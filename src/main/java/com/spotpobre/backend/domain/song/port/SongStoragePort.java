package com.spotpobre.backend.domain.song.port;

import com.spotpobre.backend.domain.song.model.ConfirmUploadCommand;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.SongUploadCommand;
import com.spotpobre.backend.domain.song.model.StorageObjectHead;

import java.net.URI;

public interface SongStoragePort {

    PresignedUploadResult generateUploadUrl(SongUploadCommand command);

    /**
     * Presigns an upload targeting an <b>existing</b> storage key — used by the durable
     * idempotency protocol so replays and crash recoveries of an upload initiation hand the
     * client a fresh, valid URL that writes to the exact object bound to the reserved song.
     */
    PresignedUploadResult regenerateUploadUrl(String storageKey, SongUploadCommand command);

    void confirmUpload(ConfirmUploadCommand command);

    /**
     * Verifies the stored object's actual content type and size — the integrity gate between
     * upload completion and promotion to the final key.
     */
    StorageObjectHead headObject(String storageKey);

    /**
     * Promotes an object from the staging key to its final playable key (copy, verify, then
     * delete the staging copy). Idempotent for identical bytes.
     */
    void promoteObject(String stagingKey, String finalKey);

    /** Best-effort delete used by cleanup/reconciliation and integrity quarantining. */
    void deleteObject(String storageKey);

    void abortUpload(String storageKey, String multipartUploadId);

    URI getStreamingUrl(String storageKey);
}
