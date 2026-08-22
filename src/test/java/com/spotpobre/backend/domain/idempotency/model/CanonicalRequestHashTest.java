package com.spotpobre.backend.domain.idempotency.model;

import com.spotpobre.backend.domain.common.IdempotencyKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalRequestHashTest {

    @Test
    void current_isDeterministicForIdenticalCanonicalFields() {
        List<String> fields = List.of("name=Road trip", "ownerId=u-1");

        assertEquals(CanonicalRequestHash.current(fields), CanonicalRequestHash.current(fields));
    }

    @Test
    void current_changesWhenAnyCanonicalFieldChanges() {
        CanonicalRequestHash base = CanonicalRequestHash.current(List.of("name=Road trip", "ownerId=u-1"));

        assertNotEquals(base, CanonicalRequestHash.current(List.of("name=Other", "ownerId=u-1")));
        assertNotEquals(base, CanonicalRequestHash.current(List.of("ownerId=u-1", "name=Road trip")));
    }

    @Test
    void value_isSha256Hex() {
        String hash = CanonicalRequestHash.current(List.of("a=1")).value();

        assertEquals(64, hash.length());
        assertTrue(hash.chars().allMatch(c -> Character.isDigit(c) || (c >= 'a' && c <= 'f')));
    }

    @Test
    void of_rejectsUnknownVersions() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalRequestHash.of(CanonicalRequestHash.CURRENT_VERSION + 1, List.of("a=1")));
    }
}
