package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.like.port.in.DeleteLikeUseCase;
import com.spotpobre.backend.application.like.port.in.PutLikeUseCase;
import com.spotpobre.backend.application.user.port.in.GetCurrentUserUseCase;
import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.user.model.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Locale;
import java.util.UUID;

/**
 * Desired-state like mutations. Both endpoints are idempotent and return 204 with no body:
 * repeated PUT leaves one like (original likedAt preserved), repeated DELETE leaves no like.
 */
@RestController
@RequestMapping("/api/v1/users/me/likes")
@RequiredArgsConstructor
public class LikeController {

    private final PutLikeUseCase putLikeUseCase;
    private final DeleteLikeUseCase deleteLikeUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;

    @PutMapping("/{entityType}/{entityId}")
    public ResponseEntity<Void> putLike(
            @PathVariable final String entityType,
            @PathVariable final UUID entityId,
            final Principal principal
    ) {
        putLikeUseCase.putLike(new PutLikeUseCase.PutLikeCommand(
                currentUserId(principal), entityId, parseEntityType(entityType)));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{entityType}/{entityId}")
    public ResponseEntity<Void> deleteLike(
            @PathVariable final String entityType,
            @PathVariable final UUID entityId,
            final Principal principal
    ) {
        deleteLikeUseCase.deleteLike(new DeleteLikeUseCase.DeleteLikeCommand(
                currentUserId(principal), entityId, parseEntityType(entityType)));
        return ResponseEntity.noContent().build();
    }

    private UserId currentUserId(final Principal principal) {
        return getCurrentUserUseCase.getCurrentUserId(principal.getName());
    }

    private static EntityType parseEntityType(final String rawEntityType) {
        try {
            return EntityType.valueOf(rawEntityType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid entityType: " + rawEntityType + " (expected song, artist or playlist)");
        }
    }
}
