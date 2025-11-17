package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.AuthenticateUserUseCase;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

public class AuthenticationService implements AuthenticateUserUseCase {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;

    public AuthenticationService(final AuthenticationManager authenticationManager, final UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public User authenticate(final AuthenticationCommand command) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        command.email(),
                        command.password()
                )
        );

        return userRepository.findByProfileEmail(command.email())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));
    }
}
