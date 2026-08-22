package com.spotpobre.backend.application.like.port.in;

import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.user.model.UserId;

import java.util.UUID;

/**
 * Desired-state "like" operation: ensures the actor likes the entity. Idempotent — repeated
 * commands leave exactly one like with its original likedAt.
 */
public interface PutLikeUseCase {

    void putLike(PutLikeCommand command);

    record PutLikeCommand(UserId userId, UUID entityId, EntityType entityType) {
    }
}
