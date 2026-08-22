package com.spotpobre.backend.domain.idempotency.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FailureDescriptorTest {

    @Test
    void of_acceptsDeterministic4xx() {
        FailureDescriptor failure = FailureDescriptor.of(409, "PLAYLIST_LIMIT_REACHED", "limit reached");

        assertEquals(409, failure.status());
        assertEquals("PLAYLIST_LIMIT_REACHED", failure.type());
        assertEquals("limit reached", failure.message());
    }

    @Test
    void of_rejectsNon4xxStatuses() {
        assertThrows(IllegalArgumentException.class,
                () -> FailureDescriptor.of(399, "TOO_LOW", null));
        assertThrows(IllegalArgumentException.class,
                () -> FailureDescriptor.of(500, "INTERNAL", null));
        assertThrows(IllegalArgumentException.class,
                () -> FailureDescriptor.of(503, "UNAVAILABLE", null));
    }

    @Test
    void of_requiresType() {
        assertThrows(NullPointerException.class, () -> FailureDescriptor.of(404, null, null));
    }

    @Test
    void of_nullMessageBecomesEmpty() {
        assertEquals("", FailureDescriptor.of(404, "NOT_FOUND", null).message());
    }
}
