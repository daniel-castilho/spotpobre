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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        SearchArtistsUseCase.SearchArtistsCommand command = new SearchArtistsUseCase.SearchArtistsCommand(query, pageRequest);
        PageResult<Artist> expectedPage = new PageResult<>(Collections.emptyList(), 0L, 0, 0, 10, false, false, null);

        when(artistRepository.searchByName(query, pageRequest)).thenReturn(expectedPage);

        // When
        PageResult<Artist> resultPage = searchArtistsService.searchArtists(command);

        // Then
        assertNotNull(resultPage);
        assertEquals(expectedPage, resultPage);
        verify(artistRepository, times(1)).searchByName(query, pageRequest);
    }
}
