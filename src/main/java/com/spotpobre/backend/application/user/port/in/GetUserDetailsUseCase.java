package com.spotpobre.backend.application.user.port.in;

import org.springframework.security.core.userdetails.UserDetails;

public interface GetUserDetailsUseCase {
    UserDetails loadUserByUsername(final String username);
}
