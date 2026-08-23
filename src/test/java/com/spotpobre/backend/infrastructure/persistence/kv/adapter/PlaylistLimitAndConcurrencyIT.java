package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.AbstractIntegrationTest;
import com.spotpobre.backend.application.playlist.port.in.CreatePlaylistUseCase;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        assertEquals(10, playlistRepository.findByOwnerId(ownerId, PageRequest.of(0, 50), null).content().size());
    }

    @Test
    void shouldSerializeConcurrentCreationsUnderOwnerLimit() throws Exception {
        User owner = seedUser();
        UserId ownerId = owner.getId();
        final int racers = 14;

        // Every racer fires a strictly simultaneous create for the same owner. Count-then-insert
        // let all of them observe the same count and overshoot; the transactional counter must
        // serialize them so exactly MAX_PLAYLISTS_PER_USER land and the rest are rejected.
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CyclicBarrier startLine = new CyclicBarrier(racers);
        List<Callable<Boolean>> racers_ = new ArrayList<>();
        for (int i = 0; i < racers; i++) {
            final String name = "Racer " + i;
            racers_.add(() -> {
                startLine.await();
                try {
                    createPlaylistUseCase.createPlaylist(
                            new CreatePlaylistUseCase.CreatePlaylistCommand(name, ownerId));
                    return true;
                } catch (ConflictException e) {
                    return false;
                }
            });
        }

        long accepted = 0;
        long rejected = 0;
        for (Future<Boolean> future : pool.invokeAll(racers_)) {
            if (future.get()) {
                accepted++;
            } else {
                rejected++;
            }
        }
        pool.shutdownNow();

        assertEquals(User.MAX_PLAYLISTS_PER_USER, accepted,
                "the limit must bind even under strictly concurrent creation");
        assertEquals(racers - User.MAX_PLAYLISTS_PER_USER, rejected);
        assertEquals(User.MAX_PLAYLISTS_PER_USER,
                playlistRepository.findByOwnerId(ownerId, PageRequest.of(0, 50), null).content().size(),
                "no phantom playlist may survive past the limit");
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