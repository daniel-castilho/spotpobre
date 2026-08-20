package com.spotpobre.backend.infrastructure.security.adapter;

import com.spotpobre.backend.application.user.port.in.GetUserDetailsUseCase;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.infrastructure.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

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
        // Return a Jackson-friendly DTO instead of Spring Security's User: the latter has no
        // default constructor and cannot be round-tripped through the Redis cache (S6).
        final List<String> roles = domainUser.getRoles().stream()
                .map(role -> "ROLE_" + role.name())
                .toList();

        return new CachedUserDetails(
                domainUser.getProfile().email(),
                domainUser.getPassword(), // Can be null for OAuth2 users
                roles
        );
    }
}
