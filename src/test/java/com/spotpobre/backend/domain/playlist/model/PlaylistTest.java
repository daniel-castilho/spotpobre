package com.spotpobre.backend.domain.playlist.model;

import com.spotpobre.backend.domain.album.model.AlbumId; // Import AlbumId
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.user.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PlaylistTest {

    @Test
    void shouldCreatePlaylistSuccessfully() {
        // Given
        String name = "My Rock Playlist";
        UserId ownerId = UserId.generate();

        // When
        Playlist playlist = Playlist.create(name, ownerId);

        // Then
        assertNotNull(playlist);
        assertNotNull(playlist.getId());
        assertEquals(name, playlist.getName());
        assertEquals(ownerId, playlist.getOwnerId());
        assertNotNull(playlist.getSongs());
        assertTrue(playlist.getSongs().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void shouldThrowExceptionWhenCreatingWithBlankName(String blankName) {
        // Given
        UserId ownerId = UserId.generate();

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Playlist.create(blankName, ownerId);
        });
        assertEquals("Playlist name cannot be blank.", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenCreatingWithNullOwnerId() {
        // Given
        String name = "A Playlist with No Owner";

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Playlist.create(name, null);
        });
        assertEquals("Owner ID cannot be null.", exception.getMessage());
    }

    @Test
    void shouldAddSongSuccessfully() {
        // Given
        Playlist playlist = Playlist.create("My Rock Playlist", UserId.generate());
        // Corrected: Provide a valid AlbumId when creating the song
        Song newSong = Song.create("Stairway to Heaven", new AlbumId(UUID.randomUUID()), "storage-id-123");

        // When
        playlist.addSong(newSong);

        // Then
        assertNotNull(playlist.getSongs());
        assertEquals(1, playlist.getSongs().size());
        assertEquals(newSong, playlist.getSongs().get(0));
    }

    @Test
    void shouldThrowExceptionWhenSongLimitIsReached() {
        // Given
        Playlist playlist = Playlist.create("Huge Playlist", UserId.generate());
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        IntStream.range(0, 100).forEach(i -> {
            Song song = Song.create("Song " + i, albumId, "storage-" + i);
            playlist.addSong(song);
        });
        Song oneMoreSong = Song.create("The 101st Song", albumId, "storage-101");

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            playlist.addSong(oneMoreSong);
        });

        assertEquals("Playlist cannot have more than 100 songs.", exception.getMessage());
        assertEquals(100, playlist.getSongs().size());
    }
}
