package com.spotpobre.backend.infrastructure.security.service;

import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.model.Role;
import com.spotpobre.backend.infrastructure.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-with-at-least-32-chars!!";
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(15)));
    }

    @Test
    void issueForMintsTokenSubjectedToEmailWithRoleClaims() {
        User user = User.builder()
                .profile(new UserProfile("Ada Lovelace", "ada@example.com", "BR"))
                .password("hashed-password")
                .roles(java.util.EnumSet.of(Role.USER, Role.ARTIST))
                .build();

        String token = jwtService.issueFor(user);

        Claims claims = Jwts.parser()
                .verifyWith(new javax.crypto.spec.SecretKeySpec(
                        SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("ada@example.com", claims.getSubject());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
        @SuppressWarnings("unchecked")
        List<String> authorities = claims.get("authorities", List.class);
        assertEquals(java.util.Set.of("ROLE_USER", "ROLE_ARTIST"), Set.copyOf(authorities));
    }
}
