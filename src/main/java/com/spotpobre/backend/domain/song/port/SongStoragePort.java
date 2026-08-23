package com.spotpobre.backend.domain.song.port;

import com.spotpobre.backend.domain.song.model.ConfirmUploadCommand;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.SongUploadCommand;

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

    void abortUpload(String storageKey, String multipartUploadId);

    URI getStreamingUrl(String storageKey);
}
