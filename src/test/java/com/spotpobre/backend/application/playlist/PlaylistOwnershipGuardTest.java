package com.spotpobre.backend.application.playlist;

import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.user.model.UserId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaylistOwnershipGuardTest {

    @Test
    void requireOwner_whenOwnerMatches_doesNotThrow() {
        UserId ownerId = UserId.generate();
        Playlist playlist = Playlist.create("Mine", ownerId);

        assertDoesNotThrow(() -> PlaylistOwnershipGuard.requireOwner(playlist, ownerId));
    }

    @Test
    void requireOwner_whenDifferentUser_throwsForbidden() {
        Playlist playlist = Playlist.create("Mine", UserId.generate());

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> PlaylistOwnershipGuard.requireOwner(playlist, UserId.generate())
        );

        assertEquals(PlaylistOwnershipGuard.ACCESS_DENIED_MESSAGE, exception.getMessage());
    }

    @Test
    void requireOwner_whenCurrentUserIdIsNull_throwsForbidden() {
        Playlist playlist = Playlist.create("Mine", UserId.generate());

        assertThrows(ForbiddenException.class, () -> PlaylistOwnershipGuard.requireOwner(playlist, null));
    }
}
