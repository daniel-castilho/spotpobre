package com.spotpobre.backend.application.song.port.in;

import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.song.model.CompletedUploadPart;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;

import java.util.List;

public interface ConfirmSongUploadUseCase {

    Song confirmUpload(ConfirmSongUploadCommand command);

    record ConfirmSongUploadCommand(
            SongId songId,
            AlbumId albumId,
            String storageKey,
            String multipartUploadId,
            List<CompletedUploadPart> completedParts
    ) {
    }
}
