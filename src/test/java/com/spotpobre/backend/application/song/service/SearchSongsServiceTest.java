package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.SearchSongsUseCase;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
        Pageable pageable = PageRequest.of(0, 10);
        SearchSongsUseCase.SearchSongsCommand command = new SearchSongsUseCase.SearchSongsCommand(query, pageable);
        Page<Song> expectedPage = new PageImpl<>(Collections.emptyList());

        when(songMetadataRepository.searchByTitle(query, pageable)).thenReturn(expectedPage);

        // When
        Page<Song> resultPage = searchSongsService.searchSongs(command);

        // Then
        assertNotNull(resultPage);
        assertEquals(expectedPage, resultPage);
        verify(songMetadataRepository, times(1)).searchByTitle(query, pageable);
    }
}
