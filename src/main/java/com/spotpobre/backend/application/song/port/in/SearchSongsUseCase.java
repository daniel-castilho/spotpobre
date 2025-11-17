package com.spotpobre.backend.application.song.port.in;

import com.spotpobre.backend.domain.song.model.Song;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SearchSongsUseCase {

    Page<Song> searchSongs(final SearchSongsCommand command);

    record SearchSongsCommand(String query, Pageable pageable) {
    }
}
