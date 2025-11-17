package com.spotpobre.backend.domain.artist.model;

import java.util.UUID;

public record ArtistId(UUID value) {
    public static ArtistId generate() {
        return new ArtistId(UUID.randomUUID());
    }

    public static ArtistId from(final String value) {
        return new ArtistId(UUID.fromString(value));
    }
}
