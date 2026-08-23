package com.spotpobre.backend.infrastructure.persistence.kv.mapper;

import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.user.model.UserId;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Null-tolerant conversions between String/UUID and the typed id value objects. */
class UuidMapperTest {

    private final UuidMapper mapper = new UuidMapperImpl();

    private static final UUID UUID_A = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void uuidToString_nullAndValue() {
        assertNull(mapper.uuidToString(null));
        assertEquals(UUID_A.toString(), mapper.uuidToString(UUID_A));
    }

    @Test
    void stringToSongId_nullAndValue() {
        assertNull(mapper.stringToSongId(null));
        assertEquals(new SongId(UUID_A), mapper.stringToSongId(UUID_A.toString()));
    }

    @Test
    void stringToArtistId_nullAndValue() {
        assertNull(mapper.stringToArtistId(null));
        assertEquals(new ArtistId(UUID_A), mapper.stringToArtistId(UUID_A.toString()));
    }

    @Test
    void stringToUserId_nullAndValue() {
        assertNull(mapper.stringToUserId(null));
        assertEquals(new UserId(UUID_A), mapper.stringToUserId(UUID_A.toString()));
    }

    @Test
    void stringToPlaylistId_nullAndValue() {
        assertNull(mapper.stringToPlaylistId(null));
        assertEquals(new PlaylistId(UUID_A), mapper.stringToPlaylistId(UUID_A.toString()));
    }

    @Test
    void stringToAlbumId_nullAndValue() {
        assertNull(mapper.stringToAlbumId(null));
        assertEquals(new AlbumId(UUID_A), mapper.stringToAlbumId(UUID_A.toString()));
    }

    @Test
    void playlistIdToUuid_nullAndValue() {
        assertNull(mapper.playlistIdToUuid(null));
        assertEquals(UUID_A, mapper.playlistIdToUuid(new PlaylistId(UUID_A)));
    }

    @Test
    void userIdToUuid_nullAndValue() {
        assertNull(mapper.userIdToUuid(null));
        assertEquals(UUID_A, mapper.userIdToUuid(new UserId(UUID_A)));
    }

    @Test
    void songIdToUuid_nullAndValue() {
        assertNull(mapper.songIdToUuid(null));
        assertEquals(UUID_A, mapper.songIdToUuid(new SongId(UUID_A)));
    }

    @Test
    void artistIdToUuid_nullAndValue() {
        assertNull(mapper.artistIdToUuid(null));
        assertEquals(UUID_A, mapper.artistIdToUuid(new ArtistId(UUID_A)));
    }

    @Test
    void albumIdToUuid_nullAndValue() {
        assertNull(mapper.albumIdToUuid(null));
        assertEquals(UUID_A, mapper.albumIdToUuid(new AlbumId(UUID_A)));
    }

    @Test
    void uuidToArtistId_nullAndValue() {
        assertNull(mapper.uuidToArtistId(null));
        assertEquals(new ArtistId(UUID_A), mapper.uuidToArtistId(UUID_A));
    }

    @Test
    void uuidToAlbumId_nullAndValue() {
        assertNull(mapper.uuidToAlbumId(null));
        assertEquals(new AlbumId(UUID_A), mapper.uuidToAlbumId(UUID_A));
    }

    @Test
    void uriToString_nullAndValue() {
        assertNull(mapper.uriToString(null));
        assertEquals("https://example.com/x", mapper.uriToString(URI.create("https://example.com/x")));
    }
}
