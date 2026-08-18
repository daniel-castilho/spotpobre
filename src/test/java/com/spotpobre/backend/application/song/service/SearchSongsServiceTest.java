package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.SearchSongsUseCase;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchSongsServiceTest {

    @Mock
    private SongMetadataRepository songMetadataRepository;

    @InjectMocks
    private SearchSongsService searchSongsService;

    @Test
    void shouldSearchSongsSuccessfully() {
        // Given
        String query = "test";
        PageRequest pageRequest = PageRequest.of(0, 10);
        SearchSongsUseCase.SearchSongsCommand command = new SearchSongsUseCase.SearchSongsCommand(query, pageRequest);
        PageResult<Song> expectedPage = new PageResult<>(Collections.emptyList(), 0L, 0, 0, 10, false, false, null);

        when(songMetadataRepository.searchByTitle(query, pageRequest)).thenReturn(expectedPage);

        // When
        PageResult<Song> resultPage = searchSongsService.searchSongs(command);

        // Then
        assertNotNull(resultPage);
        assertEquals(expectedPage, resultPage);
        verify(songMetadataRepository, times(1)).searchByTitle(query, pageRequest);
    }
}
