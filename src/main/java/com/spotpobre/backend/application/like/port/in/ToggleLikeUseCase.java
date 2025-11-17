package com.spotpobre.backend.application.like.port.in;

import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.user.model.UserId;

public interface ToggleLikeUseCase {

    LikeResult toggleLike(ToggleLikeCommand command);

    record ToggleLikeCommand(UserId userId, String entityId, EntityType entityType) {
    }

    record LikeResult(boolean isLiked, long newLikeCount) {
    }
}
