package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.ConfirmSongUploadUseCase;
import com.spotpobre.backend.domain.song.model.ConfirmUploadCommand;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import org.springframework.transaction.annotation.Transactional;

public class ConfirmSongUploadService implements ConfirmSongUploadUseCase {

    private final SongStoragePort songStoragePort;
    private final SongMetadataRepository songMetadataRepository;

    public ConfirmSongUploadService(
            final SongStoragePort songStoragePort,
            final SongMetadataRepository songMetadataRepository
    ) {
        this.songStoragePort = songStoragePort;
        this.songMetadataRepository = songMetadataRepository;
    }

    @Override
    @Transactional
    public Song confirmUpload(final ConfirmSongUploadCommand command) {
        final Song song = songMetadataRepository.findById(command.songId())
                .orElseThrow(() -> new IllegalArgumentException("Song not found: " + command.songId()));

        if (!song.getAlbumId().equals(command.albumId())) {
            throw new IllegalArgumentException("Song does not belong to album: " + command.albumId());
        }
        if (!song.getStorageId().equals(command.storageKey())) {
            throw new IllegalArgumentException("Storage key does not match the song record.");
        }

        songStoragePort.confirmUpload(new ConfirmUploadCommand(
                command.storageKey(),
                command.multipartUploadId(),
                command.completedParts()
        ));

        return song;
    }
}
