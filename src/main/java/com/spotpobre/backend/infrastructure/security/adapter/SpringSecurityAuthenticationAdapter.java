package com.spotpobre.backend.infrastructure.security.adapter;

import com.spotpobre.backend.domain.user.model.AuthenticatedUser;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.port.AuthenticationPort;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Bridges the domain {@link AuthenticationPort} to Spring Security's {@link AuthenticationManager}.
 * All Spring Security types stay confined to this adapter.
 */
@Component
@RequiredArgsConstructor
public class SpringSecurityAuthenticationAdapter implements AuthenticationPort {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;

    @Override
    public AuthenticatedUser authenticate(final String email, final String rawPassword) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, rawPassword)
        );

        final User user = userRepository.findByProfileEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));

        return new AuthenticatedUser(user);
    }
}