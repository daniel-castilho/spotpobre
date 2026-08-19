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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        SearchSongsUseCase.SearchSongsCommand command = new SearchSongsUseCase.SearchSongsCommand(query, pageRequest, null);
        PageResult<Song> expectedPage = new PageResult<>(Collections.emptyList(), 0L, 0, 0, 10, false, false, null);

        when(songMetadataRepository.searchByTitle(query, pageRequest, null)).thenReturn(expectedPage);

        // When
        PageResult<Song> resultPage = searchSongsService.searchSongs(command);

        // Then
        assertNotNull(resultPage);
        assertEquals(expectedPage, resultPage);
        verify(songMetadataRepository, times(1)).searchByTitle(query, pageRequest, null);
    }

    @Test
    void shouldForwardCursorToRepository() {
        // Given
        PageRequest pageRequest = PageRequest.of(0, 10);
        SearchSongsUseCase.SearchSongsCommand command =
                new SearchSongsUseCase.SearchSongsCommand("test", pageRequest, "opaque-cursor");
        PageResult<Song> expectedPage = new PageResult<>(Collections.emptyList(), 0L, 0, 0, 10, false, false, null);

        when(songMetadataRepository.searchByTitle("test", pageRequest, "opaque-cursor")).thenReturn(expectedPage);

        // When
        searchSongsService.searchSongs(command);

        // Then
        verify(songMetadataRepository, times(1)).searchByTitle("test", pageRequest, "opaque-cursor");
    }

    @Test
    void shouldRejectPageSizeAboveMaximum() {
        // Given
        PageRequest pageRequest = PageRequest.of(0, PageRequest.MAX_PAGE_SIZE + 1);
        SearchSongsUseCase.SearchSongsCommand command = new SearchSongsUseCase.SearchSongsCommand("test", pageRequest, null);

        // When / Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> searchSongsService.searchSongs(command));
        assertEquals("pageSize must not exceed " + PageRequest.MAX_PAGE_SIZE, exception.getMessage());
    }
}