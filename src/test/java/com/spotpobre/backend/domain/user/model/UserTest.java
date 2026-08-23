package com.spotpobre.backend.domain.user.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private UserProfile validProfile;

    @BeforeEach
    void setUp() {
        validProfile = new UserProfile("Test User", "test@example.com", "BR");
    }

    @Test
    void shouldCreateUserWithLocalPasswordSuccessfully() {
        // When
        User user = User.createWithLocalPassword(validProfile, "password123");

        // Then
        assertNotNull(user);
        assertNotNull(user.getId());
        assertEquals(validProfile, user.getProfile());
        assertEquals("password123", user.getPassword());
        assertNotNull(user.getRoles());
        assertEquals(1, user.getRoles().size());
        assertTrue(user.getRoles().contains(Role.USER));
    }

    @Test
    void shouldCreateUserFromExternalProviderSuccessfully() {
        // When
        User user = User.createFromExternalProvider(validProfile);

        // Then
        assertNotNull(user);
        assertNotNull(user.getId());
        assertEquals(validProfile, user.getProfile());
        assertNull(user.getPassword()); // No password for external provider
        assertEquals(1, user.getRoles().size());
        assertTrue(user.getRoles().contains(Role.USER));
    }

    @Test
    void shouldThrowExceptionWhenCreatingWithNullProfile() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            User.createWithLocalPassword(null, "password123");
        });
        assertEquals("User profile cannot be null.", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void shouldThrowExceptionWhenCreatingWithBlankPassword(String blankPassword) {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            User.createWithLocalPassword(validProfile, blankPassword);
        });
        assertEquals("Password cannot be blank for local registration.", exception.getMessage());
    }

    @Test
    void shouldGrantAndRevokeRolesCorrectly() {
        // Given
        User user = User.createWithLocalPassword(validProfile, "password123");
        assertTrue(user.getRoles().contains(Role.USER));
        assertFalse(user.getRoles().contains(Role.ADMIN));

        // When
        user.grantRole(Role.ADMIN);

        // Then
        assertEquals(2, user.getRoles().size());
        assertTrue(user.getRoles().contains(Role.ADMIN));

        // When
        user.revokeRole(Role.USER);

        // Then
        assertEquals(1, user.getRoles().size());
        assertFalse(user.getRoles().contains(Role.USER));
        assertTrue(user.getRoles().contains(Role.ADMIN));
    }

    @Test
    void equals_hashCode_coverAllFieldCombinations() {
        User base = User.createWithLocalPassword(new UserProfile("A", "a@x.com", "BR"), "pw1");

        assertEquals(base, base);
        assertEquals(base.hashCode(), User.builder()
                .id(base.getId()).profile(base.getProfile())
                .password("pw1").roles(base.getRoles()).build().hashCode());
        assertNotEquals(null, base);
        assertNotEquals("not a user", base);

        // One differing field at a time (each Objects.equals argument pair).
        assertNotEquals(base, User.builder().id(com.spotpobre.backend.domain.user.model.UserId.generate())
                .profile(base.getProfile()).password("pw1").roles(base.getRoles()).build());
        assertNotEquals(base, User.builder().id(base.getId())
                .profile(new UserProfile("B", "b@x.com", "US")).password("pw1").roles(base.getRoles()).build());
        assertNotEquals(base, User.builder().id(base.getId()).profile(base.getProfile())
                .password("other").roles(base.getRoles()).build());
        assertNotEquals(base, User.builder().id(base.getId()).profile(base.getProfile())
                .password("pw1").roles(java.util.EnumSet.of(Role.ADMIN)).build());
        // Null-field side of each pair.
        User partial = User.builder().id(base.getId()).build();
        assertNotEquals(base, partial);
        assertNotEquals(partial, base);
    }

    @Test
    void toString_protectsPassword_andIncludesVerificationState() {
        User user = User.createWithLocalPassword(new UserProfile("A", "a2@x.com", "BR"), "secret");
        String s = user.toString();
        assertTrue(s.contains("[PROTECTED]"));
        assertFalse(s.contains("secret"));
        assertTrue(s.contains("emailVerifiedAt=null"));

        user.markEmailVerified(Instant.parse("2026-08-23T12:00:00Z"));
        assertTrue(user.toString().contains("2026-08-23T12:00:00Z"));
    }

    @Test
    void markEmailVerified_firstWriteWins_andRejectsNull() {
        User user = User.createWithLocalPassword(new UserProfile("V", "v@x.com", "BR"), "pw");
        assertFalse(user.isEmailVerified());

        user.markEmailVerified(Instant.parse("2026-08-23T10:00:00Z"));
        assertTrue(user.isEmailVerified());

        user.markEmailVerified(Instant.parse("2027-01-01T00:00:00Z")); // first stamp wins
        assertEquals(Instant.parse("2026-08-23T10:00:00Z"), user.getEmailVerifiedAt());

        assertThrows(IllegalArgumentException.class, () -> user.markEmailVerified(null));
    }

    @Test
    void changePassword_blankOrNull_rejected() {
        User user = User.createWithLocalPassword(new UserProfile("C", "c@x.com", "BR"), "pw");
        assertThrows(IllegalArgumentException.class, () -> user.changePassword(null));
        assertThrows(IllegalArgumentException.class, () -> user.changePassword("   "));
    }
}
