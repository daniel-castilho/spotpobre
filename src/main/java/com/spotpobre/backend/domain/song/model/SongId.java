package com.spotpobre.backend.domain.song.model;

import java.util.UUID;

public record SongId(UUID value) {
    public static SongId generate() {
        return new SongId(UUID.randomUUID());
    }

    public static SongId from(final String value) {
        return new SongId(UUID.fromString(value));
    }
}
