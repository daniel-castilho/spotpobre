package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.GetUserProfileUseCase;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class GetUserProfileService implements GetUserProfileUseCase {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public User getUserProfile(final String email) {
        return userRepository.findByProfileEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found with email: " + email));
    }
}
