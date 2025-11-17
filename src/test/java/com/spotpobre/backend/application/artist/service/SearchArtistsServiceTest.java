package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.SearchArtistsUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
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
class SearchArtistsServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @InjectMocks
    private SearchArtistsService searchArtistsService;

    @Test
    void shouldSearchArtistsSuccessfully() {
        // Given
        String query = "test";
        Pageable pageable = PageRequest.of(0, 10);
        SearchArtistsUseCase.SearchArtistsCommand command = new SearchArtistsUseCase.SearchArtistsCommand(query, pageable);
        Page<Artist> expectedPage = new PageImpl<>(Collections.emptyList());

        when(artistRepository.searchByName(query, pageable)).thenReturn(expectedPage);

        // When
        Page<Artist> resultPage = searchArtistsService.searchArtists(command);

        // Then
        assertNotNull(resultPage);
        assertEquals(expectedPage, resultPage);
        verify(artistRepository, times(1)).searchByName(query, pageable);
    }
}
