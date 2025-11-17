package com.spotpobre.backend.infrastructure.web.dto.response;

public record LikeResponse(
        boolean isLiked,
        long newLikeCount
) {
}
