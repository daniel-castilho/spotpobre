package com.spotpobre.backend.application.song.port.in;

import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.song.model.Song;

public interface UploadSongUseCase {

    Song uploadSong(final UploadSongCommand command);

    record UploadSongCommand(
            String title,
            ArtistId artistId,
            byte[] fileContent,
            String contentType
    ) {
    }
}
