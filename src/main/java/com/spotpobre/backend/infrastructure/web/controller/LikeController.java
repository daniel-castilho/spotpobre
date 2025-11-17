package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.like.port.in.ToggleLikeUseCase;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.port.UserRepository;
import com.spotpobre.backend.infrastructure.web.dto.request.ToggleLikeRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.LikeResponse;
import com.spotpobre.backend.infrastructure.web.mapper.LikeApiMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
public class LikeController {

    private final ToggleLikeUseCase toggleLikeUseCase;
    private final UserRepository userRepository;
    private final LikeApiMapper mapper;

    @PostMapping("/toggle")
    public ResponseEntity<LikeResponse> toggleLike(
            @RequestBody @Valid ToggleLikeRequest request,
            Principal principal
    ) {
        final String userEmail = principal.getName();
        final UserId ownerId = userRepository.findByProfileEmail(userEmail)
               .map(User::getId)
               .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        ToggleLikeUseCase.ToggleLikeCommand command = mapper.toCommand(request, ownerId);
        ToggleLikeUseCase.LikeResult result = toggleLikeUseCase.toggleLike(command);
        return ResponseEntity.ok(mapper.toResponse(result));
    }
}
