package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.user.port.in.GetUserProfileUseCase; // Import GetUserProfileUseCase
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.infrastructure.web.dto.response.UserProfileResponse;
import com.spotpobre.backend.infrastructure.web.mapper.UserApiMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final GetUserProfileUseCase getUserProfileUseCase; // Use GetUserProfileUseCase
    private final UserApiMapper mapper;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        
        final User user = getUserProfileUseCase.getUserProfile(userDetails.getUsername()); // Use the new UseCase

        final UserProfileResponse response = mapper.toResponse(user);
        return ResponseEntity.ok(response);
    }
}
