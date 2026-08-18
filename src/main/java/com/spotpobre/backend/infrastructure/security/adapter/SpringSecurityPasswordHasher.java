package com.spotpobre.backend.infrastructure.security.adapter;

import com.spotpobre.backend.domain.user.port.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter that implements the {@link PasswordHasher} port with the Spring Security
 * {@link PasswordEncoder} configured in {@code SecurityConfig}. This is the only class that knows
 * which concrete algorithm (currently Argon2id) backs password hashing; swapping algorithms means
 * swapping the {@code PasswordEncoder} bean here, leaving the application layer untouched.
 */
@Component
@RequiredArgsConstructor
public class SpringSecurityPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String encode(final String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(final String rawPassword, final String hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }
}