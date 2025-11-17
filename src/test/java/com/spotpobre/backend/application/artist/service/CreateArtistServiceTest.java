package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.CreateArtistUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateArtistServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @InjectMocks
    private CreateArtistService createArtistService;

    @Test
    void shouldCreateArtistSuccessfully() {
        // Given
        CreateArtistUseCase.CreateArtistCommand command = new CreateArtistUseCase.CreateArtistCommand("New Artist");

        // When
        Artist createdArtist = createArtistService.createArtist(command);

        // Then
        assertNotNull(createdArtist);
        assertEquals("New Artist", createdArtist.getName());

        // Capture the artist argument passed to save and verify it
        ArgumentCaptor<Artist> artistCaptor = ArgumentCaptor.forClass(Artist.class);
        verify(artistRepository, times(1)).save(artistCaptor.capture());
        
        Artist savedArtist = artistCaptor.getValue();
        assertEquals("New Artist", savedArtist.getName());
    }
}
