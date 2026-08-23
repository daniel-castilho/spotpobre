package com.spotpobre.backend.infrastructure.persistence.kv.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Value-contract coverage: equals/hashCode/toString/accessors (JaCoCo branch floor). */
class SongDocumentTest {

    private SongDocument sample() {
        SongDocument doc = new SongDocument();
        doc.setId("s-1");
        doc.setTitle("T");
        doc.setSearchTitle("t");
        doc.setStorageId("st");
        doc.setAlbumId(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));
        return doc;
    }

    @Test
    void equals_reflexive_symmetric_null_and_differentType() {
        SongDocument a = sample();
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
        SongDocument empty1 = new SongDocument();
        SongDocument empty2 = new SongDocument();
        assertEquals(empty1, empty2);
        assertEquals(empty1.hashCode(), empty2.hashCode());
        assertDoesNotThrow(empty1::toString);
        assertNotEquals(sample(), empty1);
    }
}
