package com.spotpobre.backend.domain.like.model;

import com.spotpobre.backend.domain.user.model.UserId;
import java.time.Instant;

public record Like(
        UserId userId,
        String entityId,
        EntityType entityType,
        Instant likedAt
) {
}
