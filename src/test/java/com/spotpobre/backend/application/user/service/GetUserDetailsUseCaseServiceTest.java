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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetUserDetailsUseCaseServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GetUserDetailsUseCaseService getUserDetailsUseCaseService;

    @Test
    void shouldLoadUserByUsernameSuccessfully() {
        // Given
        String email = "user@example.com";
        User domainUser = User.createWithLocalPassword(new UserProfile("Test User", email, "BR"), "hashedPassword");
        domainUser.grantRole(Role.ADMIN);

        when(userRepository.findByProfileEmail(email)).thenReturn(Optional.of(domainUser));

        // When
        UserDetails userDetails = getUserDetailsUseCaseService.loadUserByUsername(email);

        // Then
        assertNotNull(userDetails);
        assertEquals(email, userDetails.getUsername());
        assertEquals("hashedPassword", userDetails.getPassword());
        assertEquals(2, userDetails.getAuthorities().size());
        assertTrue(userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertTrue(userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void shouldThrowUsernameNotFoundException() {
        // Given
        String email = "notfound@example.com";
        when(userRepository.findByProfileEmail(email)).thenReturn(Optional.empty());

        // When & Then
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class, () -> {
            getUserDetailsUseCaseService.loadUserByUsername(email);
        });

        assertEquals("User not found with email: " + email, exception.getMessage());
    }
}
