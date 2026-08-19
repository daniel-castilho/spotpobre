package com.spotpobre.backend.application.user.port.in;

import com.spotpobre.backend.domain.user.model.UserId;

public interface GetCurrentUserUseCase {

    UserId getCurrentUserId(String email);
}