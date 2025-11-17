package com.spotpobre.backend.domain.user.model;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.IntStream;

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
        assertNotNull(user.getPlaylists());
        assertTrue(user.getPlaylists().isEmpty());
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
    void shouldAddPlaylistSuccessfully() {
        // Given
        User user = User.createWithLocalPassword(validProfile, "pass");

        // When
        Playlist newPlaylist = user.createPlaylist("My Favorite Songs");

        // Then
        assertNotNull(user.getPlaylists());
        assertEquals(1, user.getPlaylists().size());
        assertEquals(newPlaylist, user.getPlaylists().get(0));
        assertEquals("My Favorite Songs", user.getPlaylists().get(0).getName());
    }

    @Test
    void shouldThrowExceptionWhenPlaylistLimitIsReached() {
        // Given
        User user = User.createWithLocalPassword(validProfile, "pass");
        IntStream.range(0, 10).forEach(i -> user.createPlaylist("Playlist " + i));

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            user.createPlaylist("One More Playlist");
        });

        assertEquals("User cannot have more than 10 playlists.", exception.getMessage());
        assertEquals(10, user.getPlaylists().size());
    }
}
