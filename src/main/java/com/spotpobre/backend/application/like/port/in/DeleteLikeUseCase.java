package com.spotpobre.backend.application.like.port.in;

import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.user.model.UserId;

import java.util.UUID;

/**
 * Desired-state "unlike" operation: ensures the actor does not like the entity. Idempotent —
 * deleting an absent like is a successful no-op.
 */
public interface DeleteLikeUseCase {

    void deleteLike(DeleteLikeCommand command);

    record DeleteLikeCommand(UserId userId, UUID entityId, EntityType entityType) {
    }
}
