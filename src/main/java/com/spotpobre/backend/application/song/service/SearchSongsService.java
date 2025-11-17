package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.SearchSongsUseCase;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class SearchSongsService implements SearchSongsUseCase {

    private final SongMetadataRepository songMetadataRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<Song> searchSongs(final SearchSongsCommand command) {
        return songMetadataRepository.searchByTitle(command.query(), command.pageable());
    }
}
