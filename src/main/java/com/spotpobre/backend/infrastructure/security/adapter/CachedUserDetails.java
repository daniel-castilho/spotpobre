package com.spotpobre.backend.infrastructure.security.adapter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Jackson-friendly {@link UserDetails} used as the cached value of the auth user cache.
 *
 * <p>Spring Security's own {@link org.springframework.security.core.userdetails.User} cannot be
 * deserialized by {@code GenericJackson2JsonRedisSerializer} because it has no default constructor
 * and no {@code @JsonCreator} (S6). This DTO has a no-arg constructor and plain getters/setters, so
 * it round-trips through Redis safely; {@link GrantedAuthority}s are rebuilt on the fly.
 */
public class CachedUserDetails implements UserDetails {

    private String username;
    private String password;
    private List<String> roles;

    public CachedUserDetails() {
        // No-arg constructor required by the JSON deserializer.
    }

    public CachedUserDetails(final String username, final String password, final List<String> roles) {
        this.username = username;
        this.password = password;
        this.roles = roles;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(final List<String> roles) {
        this.roles = roles;
    }

    @Override
    @JsonIgnore
    public Set<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return true;
    }
}