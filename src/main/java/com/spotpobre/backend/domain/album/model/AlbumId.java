package com.spotpobre.backend.domain.album.model;

import java.util.UUID;

public record AlbumId(UUID value) {
    public static AlbumId generate() {
        return new AlbumId(UUID.randomUUID());
    }

    public static AlbumId from(String uuid) {
        return new AlbumId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
