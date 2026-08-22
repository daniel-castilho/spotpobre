package com.spotpobre.backend.domain.playlist.model;

import com.spotpobre.backend.domain.album.model.AlbumId; // Import AlbumId
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
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
    void shouldEnsureSongPresentWhenAbsent() {
        // Given
        Playlist playlist = Playlist.create("My Rock Playlist", UserId.generate());
        Song newSong = Song.create("Stairway to Heaven", new AlbumId(UUID.randomUUID()), "storage-id-123");

        // When
        boolean changed = playlist.ensureSongPresent(newSong);

        // Then
        assertTrue(changed);
        assertEquals(1, playlist.getSongs().size());
        assertEquals(newSong, playlist.getSongs().get(0));
        assertTrue(playlist.containsSong(newSong.getId()));
    }

    @Test
    void shouldReturnFalseWithoutStateChangeWhenSongAlreadyPresent() {
        // Given
        Playlist playlist = Playlist.create("My Rock Playlist", UserId.generate());
        Song newSong = Song.create("Stairway to Heaven", new AlbumId(UUID.randomUUID()), "storage-id-123");
        playlist.ensureSongPresent(newSong);
        long versionBefore = playlist.getVersion();

        // When
        boolean changed = playlist.ensureSongPresent(songWithId(newSong.getId()));

        // Then
        assertFalse(changed);
        assertEquals(1, playlist.getSongs().size());
        assertEquals(versionBefore, playlist.getVersion());
        assertTrue(playlist.containsSong(newSong.getId()));
    }

    @Test
    void shouldThrowExceptionWhenUniqueSongLimitIsReached() {
        // Given
        Playlist playlist = Playlist.create("Huge Playlist", UserId.generate());
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        IntStream.range(0, 100).forEach(i -> {
            Song song = Song.create("Song " + i, albumId, "storage-" + i);
            playlist.ensureSongPresent(song);
        });
        Song oneMoreSong = Song.create("The 101st Song", albumId, "storage-101");

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            playlist.ensureSongPresent(oneMoreSong);
        });

        assertEquals("Playlist cannot have more than 100 songs.", exception.getMessage());
        assertEquals(100, playlist.getSongs().size());
    }

    @Test
    void shouldAllowRepeatedPutOfExistingSongAtLimit() {
        // Given: playlist at MAX_SONGS with a specific song present
        Playlist playlist = Playlist.create("Huge Playlist", UserId.generate());
        AlbumId albumId = new AlbumId(UUID.randomUUID());
        Song existingSong = Song.create("Already There", albumId, "storage-existing");
        songWithIdInto(existingSong, playlist);
        IntStream.range(0, 99).forEach(i -> {
            Song song = Song.create("Song " + i, albumId, "storage-" + i);
            playlist.ensureSongPresent(song);
        });

        // When: repeated PUT of the existing song at the limit must stay a successful no-op
        boolean changed = playlist.ensureSongPresent(songWithId(existingSong.getId()));

        // Then
        assertFalse(changed);
        assertEquals(100, playlist.getSongs().size());
    }

    @Test
    void shouldEnsureSongAbsentWhenPresent() {
        // Given
        Playlist playlist = Playlist.create("My Rock Playlist", UserId.generate());
        Song song = Song.create("Stairway to Heaven", new AlbumId(UUID.randomUUID()), "storage-id-123");
        playlist.ensureSongPresent(song);

        // When
        boolean changed = playlist.ensureSongAbsent(song.getId());

        // Then
        assertTrue(changed);
        assertTrue(playlist.getSongs().isEmpty());
        assertFalse(playlist.containsSong(song.getId()));
    }

    @Test
    void shouldReturnFalseWithoutStateChangeWhenSongAlreadyAbsent() {
        // Given
        Playlist playlist = Playlist.create("My Rock Playlist", UserId.generate());
        long versionBefore = playlist.getVersion();

        // When
        boolean changed = playlist.ensureSongAbsent(new SongId(UUID.randomUUID()));

        // Then
        assertFalse(changed);
        assertEquals(versionBefore, playlist.getVersion());
    }

    private static Song songWithId(final SongId songId) {
        Song song = Song.create("Same Id Song", new AlbumId(UUID.randomUUID()), "storage-same");
        song.setId(songId);
        return song;
    }

    private static void songWithIdInto(final Song song, final Playlist playlist) {
        playlist.ensureSongPresent(song);
    }
}
