package com.spotpobre.backend.infrastructure.security.adapter;

import com.spotpobre.backend.domain.user.model.AuthenticatedUser;
import com.spotpobre.backend.domain.user.model.Role;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpringSecurityAuthenticationAdapterTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SpringSecurityAuthenticationAdapter adapter;

    @Test
    void shouldAuthenticateSuccessfullyAndReturnAuthenticatedUser() {
        // Given
        String email = "user@example.com";
        String password = "password";
        User expectedUser = User.createWithLocalPassword(new UserProfile("Test User", email, "BR"), "hashedPassword");
        expectedUser.grantRole(Role.ADMIN);

        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(userRepository.findByProfileEmail(email)).thenReturn(Optional.of(expectedUser));

        // When
        AuthenticatedUser result = adapter.authenticate(email, password);

        // Then
        assertNotNull(result);
        assertEquals(expectedUser, result.user());
        verify(authenticationManager, times(1)).authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );
        verify(userRepository, times(1)).findByProfileEmail(email);
    }

    @Test
    void shouldThrowWhenCredentialsAreInvalid() {
        // Given
        String email = "user@example.com";
        String password = "wrong-password";

        doThrow(new BadCredentialsException("Bad credentials")).when(authenticationManager).authenticate(any());

        // When & Then
        assertThrows(BadCredentialsException.class, () -> adapter.authenticate(email, password));
        verify(userRepository, never()).findByProfileEmail(any());
    }

    @Test
    void shouldThrowWhenUserIsNotFoundAfterSuccessfulAuth() {
        // Given
        String email = "user@example.com";
        String password = "password";

        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(userRepository.findByProfileEmail(email)).thenReturn(Optional.empty());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> adapter.authenticate(email, password));
        assertEquals("Authenticated user not found in database.", exception.getMessage());
    }
}