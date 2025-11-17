package com.spotpobre.backend.infrastructure.web.dto.request;

import com.spotpobre.backend.domain.like.model.EntityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ToggleLikeRequest(
        @NotBlank
        String entityId,
        @NotNull
        EntityType entityType
) {
}
