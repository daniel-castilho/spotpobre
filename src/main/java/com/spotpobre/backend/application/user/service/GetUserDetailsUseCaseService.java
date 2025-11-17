package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.GetUserDetailsUseCase;
import com.spotpobre.backend.domain.user.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Set;
import java.util.stream.Collectors;

public class GetUserDetailsUseCaseService implements GetUserDetailsUseCase {

    private final UserRepository userRepository;

    public GetUserDetailsUseCaseService(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
        return userRepository.findByProfileEmail(username)
                .map(domainUser -> {
                    final Set<GrantedAuthority> authorities = domainUser.getRoles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                            .collect(Collectors.toSet());

                    return new User(
                            domainUser.getProfile().email(),
                            domainUser.getPassword(), // Can be null for OAuth2 users
                            authorities
                    );
                })
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
    }
}
