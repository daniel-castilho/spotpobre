package com.spotpobre.backend.infrastructure.web.dto.response;

import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String name,
        String email,
        String country,
        Set<String> roles
) {
}
