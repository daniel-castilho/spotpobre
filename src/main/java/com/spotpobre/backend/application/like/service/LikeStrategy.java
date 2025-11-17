package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.domain.like.model.EntityType;

public interface LikeStrategy {
    boolean supports(EntityType entityType);
    void validateEntityExists(String entityId);
}
