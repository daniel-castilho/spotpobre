package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.AuthenticateUserUseCase;
import com.spotpobre.backend.domain.user.port.AuthTokenIssuer;
import com.spotpobre.backend.domain.user.port.AuthenticationPort;
import org.springframework.transaction.annotation.Transactional;

public class AuthenticationService implements AuthenticateUserUseCase {

    private final AuthenticationPort authenticationPort;
    private final AuthTokenIssuer authTokenIssuer;

    public AuthenticationService(final AuthenticationPort authenticationPort,
                                 final AuthTokenIssuer authTokenIssuer) {
        this.authenticationPort = authenticationPort;
        this.authTokenIssuer = authTokenIssuer;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedSession authenticate(final AuthenticationCommand command) {
        final var authenticated = authenticationPort.authenticate(command.email(), command.password());
        return new AuthenticatedSession(authenticated.user(), authTokenIssuer.issueFor(authenticated.user()));
    }
}
