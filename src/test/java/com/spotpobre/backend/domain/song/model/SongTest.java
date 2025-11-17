package com.spotpobre.backend.domain.song.model;

import com.spotpobre.backend.domain.artist.model.ArtistId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SongTest {

    @Test
    void shouldCreateSongSuccessfully() {
        // Given
        String title = "Bohemian Rhapsody";
        ArtistId artistId = new ArtistId(UUID.randomUUID());
        String storageId = "s3-key-for-bohemian-rhapsody";

        // When
        Song song = Song.create(title, artistId, storageId);

        // Then
        assertNotNull(song);
        assertNotNull(song.getId());
        assertEquals(title, song.getTitle());
        assertEquals(artistId, song.getArtistId());
        assertEquals(storageId, song.getStorageId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void shouldThrowExceptionWhenTitleIsBlank(String blankTitle) {
        // Given
        ArtistId artistId = new ArtistId(UUID.randomUUID());
        String storageId = "storage-id";

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Song.create(blankTitle, artistId, storageId);
        });
        assertEquals("Song title cannot be blank.", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenArtistIdIsNull() {
        // Given
        String title = "A Song with No Artist";
        String storageId = "storage-id";

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Song.create(title, null, storageId);
        });
        assertEquals("Artist ID cannot be null.", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void shouldThrowExceptionWhenStorageIdIsBlank(String blankStorageId) {
        // Given
        String title = "A Song with No Storage";
        ArtistId artistId = new ArtistId(UUID.randomUUID());

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Song.create(title, artistId, blankStorageId);
        });
        assertEquals("Storage ID cannot be blank.", exception.getMessage());
    }
}
