package com.spotpobre.backend.domain.idempotency.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaseTokenTest {

    @Test
    void generate_producesDistinctTokensWithStableHashes() {
        LeaseToken first = LeaseToken.generate();
        LeaseToken second = LeaseToken.generate();

        assertNotEquals(first.token(), second.token());
        assertNotEquals(first.hash(), second.hash());
        assertEquals(64, first.hash().length(), "hash must be SHA-256 hex");
    }

    @Test
    void fromHash_roundTripsTheHashOnly() {
        LeaseToken original = LeaseToken.generate();

        LeaseToken restored = LeaseToken.fromHash(original.hash());

        assertEquals(original.hash(), restored.hash());
        assertEquals(original, restored);
    }

    @Test
    void toString_neverExposesRawToken() {
        LeaseToken token = LeaseToken.generate();

        String rendered = token.toString();

        assertTrue(!rendered.contains(token.token()));
        assertTrue(rendered.contains(token.hash().substring(0, 10)));
    }

    @Test
    void fromHash_referenceHasNoRawToken() {
        LeaseToken reference = LeaseToken.fromHash(LeaseToken.generate().hash());

        assertThrows(IllegalStateException.class, reference::token);
    }

    @Test
    void fromHash_rejectsMalformedInput() {
        assertThrows(Exception.class, () -> LeaseToken.fromHash(null));
        assertThrows(Exception.class, () -> LeaseToken.fromHash("not-a-hash"));
    }
}
