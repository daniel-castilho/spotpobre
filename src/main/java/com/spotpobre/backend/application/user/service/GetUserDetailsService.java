package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.GetUserDetailsUseCase;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.port.UserRepository;

import java.util.Optional;

public class GetUserDetailsService implements GetUserDetailsUseCase {

    private final UserRepository userRepository;

    public GetUserDetailsService(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<User> loadUserByUsername(final String username) {
        return userRepository.findByProfileEmail(username);
    }
}