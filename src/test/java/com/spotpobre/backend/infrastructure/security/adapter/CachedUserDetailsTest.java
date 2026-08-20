package com.spotpobre.backend.infrastructure.security.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedUserDetailsTest {

    private static final GenericJackson2JsonRedisSerializer SERIALIZER =
            new GenericJackson2JsonRedisSerializer();

    @Test
    void shouldRoundTripThroughGenericJackson2JsonRedisSerializer() {
        // Given
        CachedUserDetails original = new CachedUserDetails(
                "user@example.com",
                "hashedPassword",
                List.of("ROLE_USER", "ROLE_ADMIN")
        );

        // When
        byte[] serialized = SERIALIZER.serialize(original);
        Object restored = SERIALIZER.deserialize(serialized);

        // Then
        assertTrue(restored instanceof CachedUserDetails);
        CachedUserDetails cached = (CachedUserDetails) restored;
        assertEquals("user@example.com", cached.getUsername());
        assertEquals("hashedPassword", cached.getPassword());
        assertEquals(List.of("ROLE_USER", "ROLE_ADMIN"), cached.getRoles());
        assertEquals(2, cached.getAuthorities().size());
        assertTrue(cached.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void shouldBeAValidUserDetailsContract() {
        // Given
        CachedUserDetails details = new CachedUserDetails(
                "user@example.com",
                "hashedPassword",
                List.of("ROLE_USER")
        );

        // Then
        assertTrue(details.isAccountNonExpired());
        assertTrue(details.isAccountNonLocked());
        assertTrue(details.isCredentialsNonExpired());
        assertTrue(details.isEnabled());
    }
}