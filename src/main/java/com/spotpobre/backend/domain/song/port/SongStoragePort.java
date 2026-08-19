package com.spotpobre.backend.domain.song.port;

import com.spotpobre.backend.domain.song.model.ConfirmUploadCommand;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.SongUploadCommand;

import java.net.URI;

public interface SongStoragePort {

    PresignedUploadResult generateUploadUrl(SongUploadCommand command);

    void confirmUpload(ConfirmUploadCommand command);

    void abortUpload(String storageKey, String multipartUploadId);

    URI getStreamingUrl(String storageKey);
}
