package com.spotpobre.backend.application.song.port.in;

import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.song.model.Song;

public interface UploadSongUseCase {

    Song uploadSong(final UploadSongCommand command);

    record UploadSongCommand(
            String title,
            AlbumId albumId,
            byte[] fileContent,
            String contentType
    ) {
    }
}
