package com.spotpobre.backend.domain.idempotency.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResultSnapshotTest {

    @Test
    void jsonBody_usesAllowlistedDefaults() {
        ResultSnapshot snapshot = ResultSnapshot.jsonBody("{\"id\":\"p-1\"}");

        assertEquals(200, snapshot.responseStatus());
        assertEquals("application/json", snapshot.responseContentType());
        assertEquals("{\"id\":\"p-1\"}", snapshot.body());
    }

    @Test
    void of_acceptsCreatedWithRelativeLocation() {
        ResultSnapshot snapshot = ResultSnapshot.of(201, "application/json", "/api/v1/playlists/p-1", null);

        assertEquals(201, snapshot.responseStatus());
        assertEquals("/api/v1/playlists/p-1", snapshot.location());
    }

    @Test
    void rejectsNon2xxStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> ResultSnapshot.of(199, "application/json", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> ResultSnapshot.of(300, "application/json", null, null));
    }

    @Test
    void rejectsContentTypesOutsideAllowlist() {
        assertThrows(IllegalArgumentException.class,
                () -> ResultSnapshot.of(200, "text/html", null, "<html/>"));
    }

    @Test
    void rejectsAbsoluteUrlsInLocationAndBody() {
        assertThrows(IllegalArgumentException.class,
                () -> ResultSnapshot.of(201, "application/json", "https://cdn.example.com/a/b", null));
        assertThrows(IllegalArgumentException.class,
                () -> ResultSnapshot.of(200, "application/json", null, "{\"url\":\"http://evil\"}"));
    }

    @Test
    void rejectsJwtFragmentsAndPasswordsInBody() {
        assertThrows(IllegalArgumentException.class,
                () -> ResultSnapshot.jsonBody("{\"token\":\"eyJhbGciOi...\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> ResultSnapshot.jsonBody("{\"password\":\"hunter2\"}"));
    }

    @Test
    void rejectsOversizedBodies() {
        String tooBig = "x".repeat(ResultSnapshot.MAX_BODY_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () -> ResultSnapshot.jsonBody(tooBig));
    }
}
