package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.AuthenticateUserUseCase;
import com.spotpobre.backend.domain.user.model.AuthenticatedUser;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.AuthenticationPort;
import com.spotpobre.backend.domain.user.port.AuthTokenIssuer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AuthenticationPort authenticationPort;

    @Mock
    private AuthTokenIssuer authTokenIssuer;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void shouldAuthenticateSuccessfullyAndReturnSessionWithFreshToken() {
        // Given
        AuthenticateUserUseCase.AuthenticationCommand command = new AuthenticateUserUseCase.AuthenticationCommand("user@example.com", "password");
        User expectedUser = User.createWithLocalPassword(new UserProfile("Test User", "user@example.com", "BR"), "hashedPassword");

        when(authenticationPort.authenticate(command.email(), command.password()))
                .thenReturn(new AuthenticatedUser(expectedUser));
        when(authTokenIssuer.issueFor(expectedUser)).thenReturn("signed.jwt.value");

        // When
        AuthenticateUserUseCase.AuthenticatedSession session = authenticationService.authenticate(command);

        // Then
        assertNotNull(session);
        assertEquals(expectedUser, session.user());
        assertEquals("signed.jwt.value", session.token());
        verify(authenticationPort, times(1)).authenticate(command.email(), command.password());
        verify(authTokenIssuer, times(1)).issueFor(expectedUser);
    }

    @Test
    void shouldPropagateExceptionWhenAuthenticationFails() {
        // Given
        AuthenticateUserUseCase.AuthenticationCommand command = new AuthenticateUserUseCase.AuthenticationCommand("user@example.com", "wrong-password");

        doThrow(new IllegalStateException("Bad credentials")).when(authenticationPort).authenticate(anyString(), anyString());

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            authenticationService.authenticate(command);
        });
        verifyNoInteractions(authTokenIssuer);
    }
}
