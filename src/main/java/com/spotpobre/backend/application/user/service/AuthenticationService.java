package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.AuthenticateUserUseCase;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.port.AuthenticationPort;
import org.springframework.transaction.annotation.Transactional;

public class AuthenticationService implements AuthenticateUserUseCase {

    private final AuthenticationPort authenticationPort;

    public AuthenticationService(final AuthenticationPort authenticationPort) {
        this.authenticationPort = authenticationPort;
    }

    @Override
    @Transactional(readOnly = true)
    public User authenticate(final AuthenticationCommand command) {
        return authenticationPort.authenticate(command.email(), command.password()).user();
    }
}
