package com.spotpobre.backend.domain.user.model;

public record UserProfile(
        String name,
        String email,
        String country
) {
}
