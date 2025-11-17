package com.spotpobre.backend.domain.user.model;

import java.util.UUID;

public record UserId(UUID value) {
    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId from(final String value) {
        return new UserId(UUID.fromString(value));
    }
}
