package com.spotpobre.backend.domain.artist.model;

import com.spotpobre.backend.domain.song.model.Song;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArtistTest {

    @Test
    void shouldCreateArtistSuccessfully() {
        // Given
        String name = "Queen";

        // When
        Artist artist = Artist.create(name);

        // Then
        assertNotNull(artist);
        assertNotNull(artist.getId());
        assertEquals(name, artist.getName());
        assertNotNull(artist.getSongs());
        assertTrue(artist.getSongs().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void shouldThrowExceptionWhenCreatingWithBlankName(String blankName) {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Artist.create(blankName);
        });
        assertEquals("Artist name cannot be blank.", exception.getMessage());
    }

    @Test
    void shouldAddSongToArtist() {
        // Given
        Artist artist = Artist.create("Led Zeppelin");
        Song song = Song.create("Stairway to Heaven", artist.getId(), "storage-id-123");

        // When
        artist.addSong(song);

        // Then
        assertNotNull(artist.getSongs());
        assertEquals(1, artist.getSongs().size());
        assertTrue(artist.getSongs().contains(song));
    }
}
