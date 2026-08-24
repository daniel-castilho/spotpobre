package com.spotpobre.backend.infrastructure.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RedactionTest {

    @Test
    void maskEmail_keepsFirstLetterAndDomainOnly() {
        assertEquals("u***@example.com", Redaction.maskEmail("user@example.com"));
        assertEquals("<blank>", Redaction.maskEmail(""));
        assertEquals("***", Redaction.maskEmail("nodomain"));
    }

    @Test
    void digest_shortensIdentifierForCorrelation() {
        String out = Redaction.digest("0123456789abcdef");
        assertEquals("01234567…", out);
        assertFalse(out.contains("89abcdef"));
    }

    @Test
    void shortStorageKey_keepsFolderPrefixOnly() {
        assertEquals("pending/abcd1234…",
                Redaction.shortStorageKey("pending/abcd1234567890"));
        assertEquals("songs/abcd1234…",
                Redaction.shortStorageKey("songs/abcd1234567890"));
    }
}
