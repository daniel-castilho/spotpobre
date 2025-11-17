package com.spotpobre.backend.application.song.port.in;

import com.spotpobre.backend.domain.song.model.SongId;

import java.net.URI;

public interface GetSongStreamUrlUseCase {
    URI getSongStreamUrl(final SongId songId);
}
