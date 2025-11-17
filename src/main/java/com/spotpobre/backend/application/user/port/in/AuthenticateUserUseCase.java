package com.spotpobre.backend.application.user.port.in;

import com.spotpobre.backend.domain.user.model.User;

public interface AuthenticateUserUseCase {

    User authenticate(final AuthenticationCommand command);

    record AuthenticationCommand(String email, String password) {
    }
}
