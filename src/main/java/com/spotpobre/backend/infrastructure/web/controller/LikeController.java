package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.like.port.in.ToggleLikeUseCase;
import com.spotpobre.backend.application.user.port.in.GetCurrentUserUseCase;
import com.spotpobre.backend.domain.user.model.UserId;
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
public class LikeController {

    private final ToggleLikeUseCase toggleLikeUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final LikeApiMapper mapper;

    public LikeController(
            final ToggleLikeUseCase toggleLikeUseCase,
            final GetCurrentUserUseCase getCurrentUserUseCase,
            final LikeApiMapper mapper
    ) {
        this.toggleLikeUseCase = toggleLikeUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.mapper = mapper;
    }

    @PostMapping("/toggle")
    public ResponseEntity<LikeResponse> toggleLike(
            @RequestBody @Valid ToggleLikeRequest request,
            Principal principal
    ) {
        final UserId userId = getCurrentUserUseCase.getCurrentUserId(principal.getName());

        final var command = mapper.toCommand(request, userId);
        final ToggleLikeUseCase.LikeResult result = toggleLikeUseCase.toggleLike(command);
        return ResponseEntity.ok(mapper.toResponse(result));
    }
}