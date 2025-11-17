package com.spotpobre.backend.application.user.port.in;

import com.spotpobre.backend.domain.user.model.User;

public interface GetUserProfileUseCase {
    User getUserProfile(final String email);
}
