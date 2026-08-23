package com.spotpobre.backend.infrastructure.persistence.kv.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Value-contract coverage: equals/hashCode/toString/accessors (JaCoCo branch floor). */
class PlaylistDocumentTest {

    private PlaylistDocument sample() {
        return PlaylistDocument.builder()
                .id("p-1").name("N").version(1L).build();
    }

    @Test
    void equals_reflexive_symmetric_null_and_differentType() {
        PlaylistDocument a = sample();
        assertEquals(a, a);
        assertEquals(a, sample());
        assertNotEquals(a, null);
        assertNotEquals(a, new Object());
    }

    @Test
    void hashCode_equalObjects_shareHashCode() {
        assertEquals(sample().hashCode(), sample().hashCode());
    }

    @Test
    void toString_toleratesPopulatedInstance() {
        assertNotNull(sample().toString());
    }

    @Test
    void equals_emptyInstances_areEqual_andToStringToleratesNulls() {
        PlaylistDocument empty1 = new PlaylistDocument();
        PlaylistDocument empty2 = new PlaylistDocument();
        assertEquals(empty1, empty2);
        assertEquals(empty1.hashCode(), empty2.hashCode());
        assertDoesNotThrow(empty1::toString);
        assertNotEquals(sample(), empty1);
    }
}
