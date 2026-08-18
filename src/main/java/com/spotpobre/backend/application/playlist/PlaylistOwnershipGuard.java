package com.spotpobre.backend.application.playlist;

import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.user.model.UserId;

public final class PlaylistOwnershipGuard {

    public static final String ACCESS_DENIED_MESSAGE = "You do not have permission to modify this playlist";

    private PlaylistOwnershipGuard() {
    }

    public static void requireOwner(final Playlist playlist, final UserId currentUserId) {
        if (playlist == null) {
            throw new IllegalArgumentException("Playlist cannot be null.");
        }
        if (currentUserId == null || !playlist.getOwnerId().equals(currentUserId)) {
            throw new ForbiddenException(ACCESS_DENIED_MESSAGE);
        }
    }
}
