package com.spotpobre.backend.application.album.service;

import com.spotpobre.backend.application.album.port.in.CreateAlbumUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateAlbumServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private AlbumRepository albumRepository;

    @InjectMocks
    private CreateAlbumService createAlbumService;

    @Test
    void shouldCreateAlbumSuccessfully() {
        // Given
        ArtistId artistId = ArtistId.generate();
        Artist artist = Artist.create("Test Artist");
        CreateAlbumUseCase.CreateAlbumCommand command = new CreateAlbumUseCase.CreateAlbumCommand(
                "Test Album",
                artistId,
                "https://cdn.example.com/cover.jpg"
        );

        when(artistRepository.findById(artistId)).thenReturn(Optional.of(artist));

        // When
        Album createdAlbum = createAlbumService.createAlbum(command);

        // Then
        assertNotNull(createdAlbum);
        assertNotNull(createdAlbum.getId());
        assertEquals("Test Album", createdAlbum.getName());
        assertEquals(artistId, createdAlbum.getArtistId());
        assertEquals("https://cdn.example.com/cover.jpg", createdAlbum.getCoverArtUrl());

        verify(albumRepository, times(1)).save(createdAlbum);
    }

    @Test
    void shouldThrowExceptionWhenArtistNotFound() {
        // Given
        ArtistId missingArtistId = ArtistId.generate();
        CreateAlbumUseCase.CreateAlbumCommand command = new CreateAlbumUseCase.CreateAlbumCommand(
                "Test Album",
                missingArtistId,
                null
        );

        when(artistRepository.findById(missingArtistId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            createAlbumService.createAlbum(command);
        });

        assertEquals("Artist not found: " + missingArtistId, exception.getMessage());

        verify(albumRepository, never()).save(any(Album.class));
    }
}