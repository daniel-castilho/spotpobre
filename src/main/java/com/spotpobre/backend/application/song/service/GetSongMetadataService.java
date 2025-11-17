package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.GetSongMetadataUseCase;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

public class GetSongMetadataService implements GetSongMetadataUseCase {

    private final SongMetadataRepository songMetadataRepository;

    public GetSongMetadataService(final SongMetadataRepository songMetadataRepository) {
        this.songMetadataRepository = songMetadataRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Song getSongMetadata(final SongId songId) {
        return songMetadataRepository.findById(songId)
                .orElseThrow(() -> new IllegalStateException("Song not found"));
    }
}
