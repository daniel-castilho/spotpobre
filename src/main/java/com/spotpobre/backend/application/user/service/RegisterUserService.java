package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.RegisterUserUseCase;
import com.spotpobre.backend.domain.common.ConflictException;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.PasswordHasher;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class RegisterUserService implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    @Override
    @Transactional
    public User registerUser(final RegisterUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Registration command cannot be null.");
        }

        final UserProfile profile = new UserProfile(
                command.name(),
                command.email(),
                command.country()
        );

        final String hashedPassword = passwordHasher.encode(command.password());

        final User newUser = User.createWithLocalPassword(profile, hashedPassword);

        if (!userRepository.createIfEmailNotExists(newUser)) {
            throw new ConflictException("User with email " + command.email() + " already exists.");
        }

        return newUser;
    }
}
