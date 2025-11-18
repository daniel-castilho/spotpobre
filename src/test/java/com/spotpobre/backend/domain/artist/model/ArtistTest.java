package com.spotpobre.backend.domain.artist.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
}
