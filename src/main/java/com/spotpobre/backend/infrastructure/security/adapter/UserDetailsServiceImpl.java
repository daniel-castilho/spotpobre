package com.spotpobre.backend.infrastructure.security.adapter;

import com.spotpobre.backend.application.user.port.in.GetUserDetailsUseCase;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.infrastructure.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final GetUserDetailsUseCase getUserDetailsUseCase;

    @Override
    @Cacheable(value = CacheConfig.USER_CACHE, key = "#username")
    public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
        // This method will only be executed if the user is not found in the 'userCache'.
        // The result will be cached for 5 minutes.
        return getUserDetailsUseCase.loadUserByUsername(username)
                .map(this::toSpringUserDetails)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
    }

    private UserDetails toSpringUserDetails(final User domainUser) {
        final Set<GrantedAuthority> authorities = domainUser.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());

        return new org.springframework.security.core.userdetails.User(
                domainUser.getProfile().email(),
                domainUser.getPassword(), // Can be null for OAuth2 users
                authorities
        );
    }
}
