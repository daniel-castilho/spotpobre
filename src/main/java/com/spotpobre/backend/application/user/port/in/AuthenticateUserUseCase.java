package com.spotpobre.backend.application.user.port.in;

import com.spotpobre.backend.domain.user.model.User;

public interface AuthenticateUserUseCase {

    /**
     * Authenticates the credentials and mints a session token through the domain
     * {@code AuthTokenIssuer} port — controllers never touch infrastructure services.
     */
    AuthenticatedSession authenticate(final AuthenticationCommand command);

    record AuthenticationCommand(String email, String password) {
    }

    /**
     * @param user  the authenticated account (profile and roles)
     * @param token the signed session token for subsequent authenticated calls
     */
    record AuthenticatedSession(User user, String token) {
    }
}
