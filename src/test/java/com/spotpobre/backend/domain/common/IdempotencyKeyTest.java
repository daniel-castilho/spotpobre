package com.spotpobre.backend.domain.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdempotencyKeyTest {

    @Test
    void acceptsKeyAtMinimumLength() {
        assertEquals("abcdefghijklmnop", IdempotencyKey.of("abcdefghijklmnop").value());
    }

    @Test
    void acceptsKeyWithAllAllowedCharacters() {
        final String raw = "abcXYZ019._:-" + "x".repeat(16);
        assertEquals(raw, IdempotencyKey.of(raw).value());
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> IdempotencyKey.of(null));
    }

    @Test
    void rejectsTooShort() {
        assertThrows(IllegalArgumentException.class, () -> IdempotencyKey.of("short-key-123"));
    }

    @Test
    void rejectsTooLong() {
        assertThrows(IllegalArgumentException.class, () -> IdempotencyKey.of("a".repeat(129)));
    }

    @Test
    void rejectsDisallowedCharacters() {
        assertThrows(IllegalArgumentException.class, () -> IdempotencyKey.of("has space inside-12"));
        assertThrows(IllegalArgumentException.class, () -> IdempotencyKey.of("unicode-ééé-12345"));
        assertThrows(IllegalArgumentException.class, () -> IdempotencyKey.of("slash/in/key-123"));
    }

    @Test
    void valueEquality() {
        assertEquals(IdempotencyKey.of("equal-keys-123456"), IdempotencyKey.of("equal-keys-123456"));
        assertNotEquals(IdempotencyKey.of("first-key-1234567"), IdempotencyKey.of("second-key-123456"));
        assertEquals(IdempotencyKey.of("hashable-key-1234").hashCode(), IdempotencyKey.of("hashable-key-1234").hashCode());
    }
}
