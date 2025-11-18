package com.spotpobre.backend.domain.song.model;

import com.spotpobre.backend.domain.album.model.AlbumId; // Import AlbumId
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
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        String storageId = "s3-key-for-bohemian-rhapsody";

        // When
        Song song = Song.create(title, albumId, storageId);

        // Then
        assertNotNull(song);
        assertNotNull(song.getId());
        assertEquals(title, song.getTitle());
        assertEquals(albumId, song.getAlbumId());
        assertEquals(storageId, song.getStorageId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void shouldThrowExceptionWhenTitleIsBlank(String blankTitle) {
        // Given
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        String storageId = "storage-id";

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Song.create(blankTitle, albumId, storageId);
        });
        assertEquals("Song title cannot be blank.", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenAlbumIdIsNull() {
        // Given
        String title = "A Song with No Album";
        String storageId = "storage-id";

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Song.create(title, null, storageId);
        });
        assertEquals("Album ID cannot be null.", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void shouldThrowExceptionWhenStorageIdIsBlank(String blankStorageId) {
        // Given
        String title = "A Song with No Storage";
        AlbumId albumId = new AlbumId(UUID.randomUUID());

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Song.create(title, albumId, blankStorageId);
        });
        assertEquals("Storage ID cannot be blank.", exception.getMessage());
    }
}
