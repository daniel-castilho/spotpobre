package com.spotpobre.backend.infrastructure.persistence.kv.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Value-contract coverage: equals/hashCode/toString/accessors (JaCoCo branch floor). */
class ArtistDocumentTest {

    private ArtistDocument sample() {
        ArtistDocument doc = new ArtistDocument();
        doc.setId("a-1");
        doc.setName("A");
        doc.setSearchName("a");
        return doc;
    }

    @Test
    void equals_reflexive_symmetric_null_and_differentType() {
        ArtistDocument a = sample();
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
        ArtistDocument empty1 = new ArtistDocument();
        ArtistDocument empty2 = new ArtistDocument();
        assertEquals(empty1, empty2);
        assertEquals(empty1.hashCode(), empty2.hashCode());
        assertDoesNotThrow(empty1::toString);
        assertNotEquals(sample(), empty1);
    }
}
