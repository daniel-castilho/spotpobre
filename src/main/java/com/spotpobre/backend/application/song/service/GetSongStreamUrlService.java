package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.GetSongStreamUrlUseCase;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

public class GetSongStreamUrlService implements GetSongStreamUrlUseCase {

    private final SongMetadataRepository songMetadataRepository;
    private final SongStoragePort songStoragePort;

    public GetSongStreamUrlService(final SongMetadataRepository songMetadataRepository, final SongStoragePort songStoragePort) {
        this.songMetadataRepository = songMetadataRepository;
        this.songStoragePort = songStoragePort;
    }

    @Override
    @Transactional(readOnly = true)
    public URI getSongStreamUrl(final SongId songId) {
        songMetadataRepository.findById(songId)
                .orElseThrow(() -> new IllegalStateException("Song not found"));

        return songStoragePort.getStreamingUrl(songId);
    }
}
