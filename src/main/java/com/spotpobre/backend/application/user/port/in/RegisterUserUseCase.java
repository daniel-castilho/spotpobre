package com.spotpobre.backend.application.user.port.in;

import com.spotpobre.backend.domain.user.model.User;

public interface RegisterUserUseCase {

    User registerUser(final RegisterUserCommand command);

    record RegisterUserCommand(
            String name,
            String email,
            String password,
            String country
    ) {
    }
}
