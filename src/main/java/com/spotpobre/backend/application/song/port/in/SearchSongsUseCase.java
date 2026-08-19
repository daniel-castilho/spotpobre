package com.spotpobre.backend.application.song.port.in;

import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.song.model.Song;

public interface SearchSongsUseCase {

    PageResult<Song> searchSongs(final SearchSongsCommand command);

    record SearchSongsCommand(String query, PageRequest pageRequest, String cursor) {
    }
}
