package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.RegisterUserUseCase;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegisterUserService registerUserService;

    @Test
    void shouldRegisterUserSuccessfully() {
        // Given
        RegisterUserUseCase.RegisterUserCommand command = new RegisterUserUseCase.RegisterUserCommand(
                "New User", "newuser@example.com", "password123", "US"
        );
        String hashedPassword = "hashedPassword123";

        when(userRepository.findByProfileEmail(command.email())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(command.password())).thenReturn(hashedPassword);

        // When
        User registeredUser = registerUserService.registerUser(command);

        // Then
        assertNotNull(registeredUser);
        assertEquals(command.email(), registeredUser.getProfile().email());
        assertEquals(hashedPassword, registeredUser.getPassword());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        
        User savedUser = userCaptor.getValue();
        assertEquals(hashedPassword, savedUser.getPassword());
        assertTrue(savedUser.getRoles().contains(com.spotpobre.backend.domain.user.model.Role.USER));
    }

    @Test
    void shouldThrowExceptionWhenUserAlreadyExists() {
        // Given
        RegisterUserUseCase.RegisterUserCommand command = new RegisterUserUseCase.RegisterUserCommand(
                "Existing User", "existing@example.com", "password123", "CA"
        );
        User existingUser = mock(User.class);

        when(userRepository.findByProfileEmail(command.email())).thenReturn(Optional.of(existingUser));

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            registerUserService.registerUser(command);
        });

        assertEquals("User with email " + command.email() + " already exists.", exception.getMessage());
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @ParameterizedTest
    @NullSource
    void shouldThrowExceptionWhenCommandIsNull(RegisterUserUseCase.RegisterUserCommand nullCommand) {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            registerUserService.registerUser(nullCommand);
        });
        assertEquals("Registration command cannot be null.", exception.getMessage());
    }
}
