package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.GetCurrentUserUseCase;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetCurrentUserService implements GetCurrentUserUseCase {

    private final UserRepository userRepository;

    @Override
    public UserId getCurrentUserId(String email) {
        return userRepository.findByProfileEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
    }
}