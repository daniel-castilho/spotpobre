package com.spotpobre.backend.domain.user.port;

import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(final UserId id);
    Optional<User> findByProfileEmail(final String email);
    void save(final User user);
}
