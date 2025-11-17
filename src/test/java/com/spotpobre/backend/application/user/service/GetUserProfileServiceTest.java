package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetUserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GetUserProfileService getUserProfileService;

    @Test
    void shouldReturnUserProfileSuccessfully() {
        // Given
        String email = "user@example.com";
        User expectedUser = User.createWithLocalPassword(new UserProfile("Test User", email, "BR"), "pass");

        when(userRepository.findByProfileEmail(email)).thenReturn(Optional.of(expectedUser));

        // When
        User foundUser = getUserProfileService.getUserProfile(email);

        // Then
        assertNotNull(foundUser);
        assertEquals(expectedUser, foundUser);
    }

    @Test
    void shouldThrowExceptionWhenUserProfileNotFound() {
        // Given
        String email = "nonexistent@example.com";

        when(userRepository.findByProfileEmail(email)).thenReturn(Optional.empty());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            getUserProfileService.getUserProfile(email);
        });

        assertEquals("User not found with email: " + email, exception.getMessage());
    }
}
