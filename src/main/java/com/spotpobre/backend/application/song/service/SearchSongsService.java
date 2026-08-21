package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.SearchSongsUseCase;
import com.spotpobre.backend.domain.common.Normalization;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class SearchSongsService implements SearchSongsUseCase {

    public static final int MAX_QUERY_LENGTH = 100;

    private final SongMetadataRepository songMetadataRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResult<Song> searchSongs(final SearchSongsCommand command) {
        if (command.pageRequest().pageSize() > PageRequest.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must not exceed " + PageRequest.MAX_PAGE_SIZE);
        }
        final String query = Normalization.trim(command.query());
        if (query == null || query.isEmpty() || query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("query must be between 1 and " + MAX_QUERY_LENGTH + " characters");
        }
        return songMetadataRepository.searchByTitle(query, command.pageRequest(), command.cursor());
    }
}
