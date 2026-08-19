package com.spotpobre.backend.domain.playlist.model;

public class PlaylistConcurrentModificationException extends IllegalStateException {

    public PlaylistConcurrentModificationException(final PlaylistId playlistId) {
        super("Playlist was modified concurrently. Reload and retry: " + playlistId);
    }
}