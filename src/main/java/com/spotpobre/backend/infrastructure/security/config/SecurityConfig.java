package com.spotpobre.backend.infrastructure.security.config;

import com.spotpobre.backend.infrastructure.security.adapter.UserDetailsServiceImpl;
import com.spotpobre.backend.infrastructure.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enables @PreAuthorize, @PostAuthorize, etc.
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll() // Authentication endpoints are public
                        .requestMatchers(HttpMethod.POST, "/api/v1/songs").hasAnyAuthority("ROLE_ARTIST", "ROLE_ADMIN") // Only artists and admins can upload songs
                        .requestMatchers(HttpMethod.GET, "/api/v1/songs/**").hasAnyAuthority("ROLE_USER", "ROLE_ARTIST", "ROLE_ADMIN") // All authenticated users can get song details
                        .requestMatchers(HttpMethod.POST, "/api/v1/playlists").hasAnyAuthority("ROLE_USER", "ROLE_ARTIST", "ROLE_ADMIN") // All authenticated users can create playlists
                        .requestMatchers(HttpMethod.GET, "/api/v1/playlists/**").hasAnyAuthority("ROLE_USER", "ROLE_ARTIST", "ROLE_ADMIN") // All authenticated users can get playlist details
                        .requestMatchers(HttpMethod.POST, "/api/v1/playlists/**/songs/**").hasAnyAuthority("ROLE_USER", "ROLE_ARTIST", "ROLE_ADMIN") // All authenticated users can add songs to playlists
                        .requestMatchers("/api/v1/users/me").hasAnyAuthority("ROLE_USER", "ROLE_ARTIST", "ROLE_ADMIN") // All authenticated users can get their profile
                        .requestMatchers(HttpMethod.POST, "/api/v1/artists").hasAuthority("ROLE_ADMIN") // Only admins can create artists
                        .anyRequest().authenticated() // All other requests require authentication
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        final DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(final AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
