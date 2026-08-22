package com.spotpobre.backend.domain.artist.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArtistAccountTest {

    private final ArtistId artistId = ArtistId.generate();
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.now();

    @Test
    void ownerFactoryCreatesOwnerPermission() {
        assertEquals(ArtistPermission.OWNER, ArtistAccount.owner(artistId, userId, now).permission());
    }

    @Test
    void managerFactoryCreatesManagerPermission() {
        assertEquals(ArtistPermission.MANAGER, ArtistAccount.manager(artistId, userId, now).permission());
    }

    @Test
    void rejectsNullFields() {
        assertThrows(NullPointerException.class, () -> new ArtistAccount(null, userId, ArtistPermission.OWNER, now));
        assertThrows(NullPointerException.class, () -> new ArtistAccount(artistId, null, ArtistPermission.OWNER, now));
        assertThrows(NullPointerException.class, () -> new ArtistAccount(artistId, userId, null, now));
        assertThrows(NullPointerException.class, () -> new ArtistAccount(artistId, userId, ArtistPermission.OWNER, null));
    }

    @Test
    void valueEquality() {
        assertEquals(ArtistAccount.owner(artistId, userId, now), ArtistAccount.owner(artistId, userId, now));
    }
}
