package com.spotpobre.backend.domain.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigestsTest {

    @Test
    void sha256Hex_matchesReferenceImplementation() throws Exception {
        byte[] expected = MessageDigest.getInstance("SHA-256")
                .digest("spotpobre".getBytes(StandardCharsets.UTF_8));

        StringBuilder hex = new StringBuilder();
        for (byte b : expected) {
            hex.append(String.format("%02x", b));
        }

        assertEquals(hex.toString(), Digests.sha256Hex("spotpobre"));
    }

    @Test
    void sha256Hex_isStableAcrossCalls() {
        assertEquals(Digests.sha256Hex("same input"), Digests.sha256Hex("same input"));
    }

    @Test
    void shortDigest_isBoundedPrefixOfTheFullDigest() {
        String full = Digests.sha256Hex("scope");

        assertEquals(full.substring(0, 10), Digests.shortDigest("scope"));
        assertTrue(Digests.shortDigest("scope").length() < full.length());
    }
}
