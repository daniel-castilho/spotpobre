package com.spotpobre.backend.domain.playlist.model;

import java.util.UUID;

public record PlaylistId(UUID value) {
    public static PlaylistId generate() {
        return new PlaylistId(UUID.randomUUID());
    }
}
