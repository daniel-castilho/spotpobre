package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.InitiateSongUploadUseCase;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongUploadCommand;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import org.springframework.transaction.annotation.Transactional;

public class InitiateSongUploadService implements InitiateSongUploadUseCase {

    private final SongStoragePort songStoragePort;
    private final SongMetadataRepository songMetadataRepository;
    private final AlbumRepository albumRepository;

    public InitiateSongUploadService(
            final SongStoragePort songStoragePort,
            final SongMetadataRepository songMetadataRepository,
            final AlbumRepository albumRepository
    ) {
        this.songStoragePort = songStoragePort;
        this.songMetadataRepository = songMetadataRepository;
        this.albumRepository = albumRepository;
    }

    @Override
    @Transactional
    public InitiateSongUploadResult initiateUpload(final InitiateSongUploadCommand command) {
        albumRepository.findById(command.albumId())
                .orElseThrow(() -> new IllegalArgumentException("Album not found: " + command.albumId()));

        final SongUploadCommand uploadCommand = new SongUploadCommand(
                command.contentType(),
                command.contentLengthBytes()
        );
        final PresignedUploadResult upload = songStoragePort.generateUploadUrl(uploadCommand);

        final Song song = Song.create(command.title(), command.albumId(), upload.storageKey());
        songMetadataRepository.save(song);

        return new InitiateSongUploadResult(song, upload);
    }
}
