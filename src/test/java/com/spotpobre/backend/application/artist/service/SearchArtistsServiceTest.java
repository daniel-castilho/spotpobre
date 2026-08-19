package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.SearchArtistsUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
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
class SearchArtistsServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @InjectMocks
    private SearchArtistsService searchArtistsService;

    @Test
    void shouldSearchArtistsSuccessfully() {
        // Given
        String query = "test";
        PageRequest pageRequest = PageRequest.of(0, 10);
        SearchArtistsUseCase.SearchArtistsCommand command = new SearchArtistsUseCase.SearchArtistsCommand(query, pageRequest, null);
        PageResult<Artist> expectedPage = new PageResult<>(Collections.emptyList(), 0L, 0, 0, 10, false, false, null);

        when(artistRepository.searchByName(query, pageRequest, null)).thenReturn(expectedPage);

        // When
        PageResult<Artist> resultPage = searchArtistsService.searchArtists(command);

        // Then
        assertNotNull(resultPage);
        assertEquals(expectedPage, resultPage);
        verify(artistRepository, times(1)).searchByName(query, pageRequest, null);
    }

    @Test
    void shouldForwardCursorToRepository() {
        // Given
        PageRequest pageRequest = PageRequest.of(0, 10);
        SearchArtistsUseCase.SearchArtistsCommand command =
                new SearchArtistsUseCase.SearchArtistsCommand("test", pageRequest, "opaque-cursor");
        PageResult<Artist> expectedPage = new PageResult<>(Collections.emptyList(), 0L, 0, 0, 10, false, false, null);

        when(artistRepository.searchByName("test", pageRequest, "opaque-cursor")).thenReturn(expectedPage);

        // When
        searchArtistsService.searchArtists(command);

        // Then
        verify(artistRepository, times(1)).searchByName("test", pageRequest, "opaque-cursor");
    }

    @Test
    void shouldRejectPageSizeAboveMaximum() {
        // Given
        PageRequest pageRequest = PageRequest.of(0, PageRequest.MAX_PAGE_SIZE + 1);
        SearchArtistsUseCase.SearchArtistsCommand command = new SearchArtistsUseCase.SearchArtistsCommand("test", pageRequest, null);

        // When / Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> searchArtistsService.searchArtists(command));
        assertEquals("pageSize must not exceed " + PageRequest.MAX_PAGE_SIZE, exception.getMessage());
    }
}