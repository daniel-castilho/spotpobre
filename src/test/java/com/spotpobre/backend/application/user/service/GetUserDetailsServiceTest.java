package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.domain.user.model.Role;
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
class GetUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GetUserDetailsService getUserDetailsService;

    @Test
    void shouldLoadUserByUsernameSuccessfully() {
        // Given
        String email = "user@example.com";
        User domainUser = User.createWithLocalPassword(new UserProfile("Test User", email, "BR"), "hashedPassword");
        domainUser.grantRole(Role.ADMIN);

        when(userRepository.findByProfileEmail(email)).thenReturn(Optional.of(domainUser));

        // When
        Optional<User> result = getUserDetailsService.loadUserByUsername(email);

        // Then
        assertTrue(result.isPresent());
        assertEquals(domainUser, result.get());
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        // Given
        String email = "notfound@example.com";
        when(userRepository.findByProfileEmail(email)).thenReturn(Optional.empty());

        // When
        Optional<User> result = getUserDetailsService.loadUserByUsername(email);

        // Then
        assertTrue(result.isEmpty());
        verify(userRepository, times(1)).findByProfileEmail(email);
    }
}