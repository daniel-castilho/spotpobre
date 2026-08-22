package com.spotpobre.backend.domain.like.port;

import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.like.model.Like;
import com.spotpobre.backend.domain.user.model.UserId;

public interface LikeRepository {

    /**
     * Creates the like only when no like exists yet for the deterministic
     * {@code (userId, entityType#entityId)} key. Returns {@code true} when this call created
     * the like; {@code false} when an existing like (and its original likedAt) was preserved.
     */
    boolean createIfAbsent(Like like);

    /**
     * Deletes the like only when it exists. Returns {@code true} when this call deleted a
     * like; {@code false} when there was nothing to delete.
     */
    boolean deleteIfPresent(UserId userId, String entityId, EntityType entityType);
}
