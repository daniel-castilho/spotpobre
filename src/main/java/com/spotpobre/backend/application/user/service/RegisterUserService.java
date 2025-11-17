package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.RegisterUserUseCase;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class RegisterUserService implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public User registerUser(final RegisterUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Registration command cannot be null.");
        }

        userRepository.findByProfileEmail(command.email()).ifPresent(user -> {
            throw new IllegalStateException("User with email " + command.email() + " already exists.");
        });

        final UserProfile profile = new UserProfile(
                command.name(),
                command.email(),
                command.country()
        );

        final String hashedPassword = passwordEncoder.encode(command.password());

        final User newUser = User.createWithLocalPassword(profile, hashedPassword);

        userRepository.save(newUser);

        return newUser;
    }
}
