package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.AuthenticateUserUseCase;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void shouldAuthenticateSuccessfullyAndReturnUser() {
        // Given
        AuthenticateUserUseCase.AuthenticationCommand command = new AuthenticateUserUseCase.AuthenticationCommand("user@example.com", "password");
        User expectedUser = User.createWithLocalPassword(new UserProfile("Test User", "user@example.com", "BR"), "hashedPassword");

        // Mock the Authentication object returned by the manager
        Authentication successfulAuth = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(successfulAuth);

        when(userRepository.findByProfileEmail(command.email())).thenReturn(Optional.of(expectedUser));

        // When
        User authenticatedUser = authenticationService.authenticate(command);

        // Then
        assertNotNull(authenticatedUser);
        assertEquals(expectedUser, authenticatedUser);

        verify(authenticationManager, times(1)).authenticate(
                new UsernamePasswordAuthenticationToken(command.email(), command.password())
        );
    }

    @Test
    void shouldThrowExceptionWhenAuthenticationFails() {
        // Given
        AuthenticateUserUseCase.AuthenticationCommand command = new AuthenticateUserUseCase.AuthenticationCommand("user@example.com", "wrong-password");

        doThrow(new AuthenticationException("Bad credentials") {}).when(authenticationManager).authenticate(any());

        // When & Then
        assertThrows(AuthenticationException.class, () -> {
            authenticationService.authenticate(command);
        });

        verify(userRepository, never()).findByProfileEmail(any());
    }

    @Test
    void shouldThrowExceptionWhenUserIsNotFoundAfterSuccessfulAuth() {
        // Given
        AuthenticateUserUseCase.AuthenticationCommand command = new AuthenticateUserUseCase.AuthenticationCommand("user@example.com", "password");

        // Correct way to simulate successful authentication
        Authentication successfulAuth = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(successfulAuth);

        // Simulate user not being in the database (data inconsistency)
        when(userRepository.findByProfileEmail(command.email())).thenReturn(Optional.empty());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            authenticationService.authenticate(command);
        });

        assertEquals("Authenticated user not found in database.", exception.getMessage());
    }
}
