package com.spotpobre.backend.domain.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NormalizationTest {

    @Test
    void trimRemovesSurroundingWhitespace() {
        assertEquals("name", Normalization.trim("  name\t"));
    }

    @Test
    void trimPreservesInnerWhitespace() {
        assertEquals("a  b", Normalization.trim(" a  b "));
    }

    @Test
    void trimReturnsNullForNull() {
        assertNull(Normalization.trim(null));
    }

    @Test
    void lowercaseIsLocaleIndependent() {
        assertEquals("user@example.com", Normalization.lowercase(" User@EXAMPLE.COM "));
        // Turkish-I problem: ROOT locale must not map I to dotless variants
        assertEquals("audio/mpeg", Normalization.lowercase("AUDIO/MPEG"));
    }

    @Test
    void uppercaseIsLocaleIndependent() {
        assertEquals("BR", Normalization.uppercase(" br "));
    }
}
