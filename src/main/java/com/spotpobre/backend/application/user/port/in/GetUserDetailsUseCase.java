package com.spotpobre.backend.application.user.port.in;

import com.spotpobre.backend.domain.user.model.User;

import java.util.Optional;

public interface GetUserDetailsUseCase {
    Optional<User> loadUserByUsername(final String username);
}