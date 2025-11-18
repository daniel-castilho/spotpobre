package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.UploadSongUseCase;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongFile;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class UploadSongService implements UploadSongUseCase {

    private final SongStoragePort songStoragePort;
    private final SongMetadataRepository songMetadataRepository;
    private final AlbumRepository albumRepository;

    @Override
    @Transactional
    public Song uploadSong(final UploadSongCommand command) {
        // Validate that the album exists
        albumRepository.findById(command.albumId())
                .orElseThrow(() -> new IllegalArgumentException("Album not found: " + command.albumId()));

        final SongFile songFile = new SongFile(command.fileContent(), command.contentType());
        final String storageId = songStoragePort.saveSong(songFile);

        final Song song = Song.create(command.title(), command.albumId(), storageId);
        songMetadataRepository.save(song);

        return song;
    }
}
