package com.spotpobre.backend.domain.like.port;

import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.like.model.Like;
import com.spotpobre.backend.domain.user.model.UserId;

import java.util.Optional;

public interface LikeRepository {

    void save(Like like);

    void delete(UserId userId, String entityId, EntityType entityType);

    Optional<Like> findById(UserId userId, String entityId, EntityType entityType);

    long countLikesByEntity(String entityId, EntityType entityType);
}
