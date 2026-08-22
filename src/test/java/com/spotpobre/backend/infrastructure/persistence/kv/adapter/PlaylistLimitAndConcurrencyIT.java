package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.AbstractIntegrationTest;
import com.spotpobre.backend.application.playlist.port.in.CreatePlaylistUseCase;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.playlist.model.PlaylistConcurrentModificationException;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.common.ConflictException;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class PlaylistLimitAndConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private CreatePlaylistUseCase createPlaylistUseCase;

    @Test
    void shouldAllowUpToTenPlaylistsAndRejectTheEleventh() {
        User owner = seedUser();
        UserId ownerId = owner.getId();

        for (int i = 1; i <= 10; i++) {
            Playlist created = createPlaylistUseCase.createPlaylist(
                    new CreatePlaylistUseCase.CreatePlaylistCommand("Playlist " + i, ownerId));
            assertNotNull(created);
        }

        ConflictException exception = assertThrows(ConflictException.class, () ->
                createPlaylistUseCase.createPlaylist(
                        new CreatePlaylistUseCase.CreatePlaylistCommand("Eleventh", ownerId)));

        assertEquals("User cannot have more than 10 playlists.", exception.getMessage());
        assertEquals(10, playlistRepository.countByOwnerId(ownerId));
    }

    @Test
    void shouldRejectConcurrentUpdateFromStaleSnapshot() {
        User owner = seedUser();
        Playlist created = createPlaylistUseCase.createPlaylist(
                new CreatePlaylistUseCase.CreatePlaylistCommand("Concurrent Playlist", owner.getId()));
        AlbumId albumId = AlbumId.generate();

        Playlist snapshotA = playlistRepository.findById(created.getId()).orElseThrow();
        Playlist snapshotB = playlistRepository.findById(created.getId()).orElseThrow();

        snapshotA.ensureSongPresent(Song.create("Song A", albumId, "storage-a"));
        playlistRepository.update(snapshotA);

        snapshotB.ensureSongPresent(Song.create("Song B", albumId, "storage-b"));
        assertThrows(PlaylistConcurrentModificationException.class, () -> playlistRepository.update(snapshotB));

        Playlist reloaded = playlistRepository.findById(created.getId()).orElseThrow();
        assertEquals(1, reloaded.getSongs().size());
        assertEquals("Song A", reloaded.getSongs().get(0).getTitle());
    }

    private User seedUser() {
        User user = User.createWithLocalPassword(
                new UserProfile("Limit User", "limit-" + UUID.randomUUID() + "@example.com", "BR"), "pass");
        userRepository.save(user);
        return user;
    }
}