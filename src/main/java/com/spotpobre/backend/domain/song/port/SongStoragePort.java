package com.spotpobre.backend.domain.song.port;

import com.spotpobre.backend.domain.song.model.SongFile;
import com.spotpobre.backend.domain.song.model.SongId;

import java.net.URI;

public interface SongStoragePort {
    String saveSong(final SongFile file);
    URI getStreamingUrl(final SongId id);
}
