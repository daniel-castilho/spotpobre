package com.spotpobre.backend.application.song.port.in;

import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.Song;

public interface InitiateSongUploadUseCase {

    InitiateSongUploadResult initiateUpload(InitiateSongUploadCommand command);

    record InitiateSongUploadCommand(
            String title,
            AlbumId albumId,
            String contentType,
            long contentLengthBytes,
            java.util.UUID actorUserId,
            boolean actorIsAdmin
    ) {
    }

    record InitiateSongUploadResult(Song song, PresignedUploadResult upload) {
    }
}
