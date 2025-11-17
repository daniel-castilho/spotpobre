package com.spotpobre.backend.application.song.port.in;

import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;

public interface GetSongMetadataUseCase {
    Song getSongMetadata(final SongId songId);
}
