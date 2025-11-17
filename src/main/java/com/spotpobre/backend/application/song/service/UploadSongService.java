package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.UploadSongUseCase;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongFile;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

public class UploadSongService implements UploadSongUseCase {

    private final SongStoragePort songStoragePort;
    private final SongMetadataRepository songMetadataRepository;

    public UploadSongService(final SongStoragePort songStoragePort, final SongMetadataRepository songMetadataRepository) {
        this.songStoragePort = songStoragePort;
        this.songMetadataRepository = songMetadataRepository;
    }

    @Override
    @Transactional
    public Song uploadSong(final UploadSongCommand command) {
        // 1. Save the file to blob storage
        final SongFile songFile = new SongFile(command.fileContent(), command.contentType());
        final String storageId = songStoragePort.saveSong(songFile);

        // 2. Create and save the song metadata
        final Song song = Song.create(command.title(), command.artistId(), storageId);
        songMetadataRepository.save(song);

        return song;
    }
}
