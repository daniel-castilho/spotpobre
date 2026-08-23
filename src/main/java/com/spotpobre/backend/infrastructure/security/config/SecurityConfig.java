package com.spotpobre.backend.infrastructure.security.config;

import com.spotpobre.backend.domain.user.model.Role;
import com.spotpobre.backend.infrastructure.security.adapter.UserDetailsServiceImpl;
import com.spotpobre.backend.infrastructure.security.filter.JwtAuthenticationFilter;
import com.spotpobre.backend.infrastructure.security.filter.RateLimitFilter;
import com.spotpobre.backend.infrastructure.security.handler.RestAccessDeniedHandler;
import com.spotpobre.backend.infrastructure.security.handler.RestAuthenticationEntryPoint;
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
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/v1/auth/**").permitAll()
                       .requestMatchers(HttpMethod.POST, "/api/v1/auth/password/recover").permitAll()
                       .requestMatchers(HttpMethod.POST, "/api/v1/auth/password/reset").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Probe endpoints used by the ALB / orchestrator — must be reachable
                        // without auth (S6). Everything else under /actuator/** requires auth.
                        .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness", "/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").authenticated()

                        // Admin-only endpoints (authorities carry the ROLE_ prefix, see GetUserDetailsService)
                        .requestMatchers(HttpMethod.POST, "/api/v1/artists").hasRole(Role.ADMIN.name())
                        .requestMatchers("/api/v1/artists/*/accounts/**").hasRole(Role.ADMIN.name())

                        // Artist-only endpoints (presigned song upload). Application-level
                        // membership checks (ArtistAccount) are enforced in the use cases.
                        .requestMatchers(HttpMethod.POST, "/api/v1/albums/*/songs").hasRole(Role.ARTIST.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/albums/*/songs/*/confirm").hasRole(Role.ARTIST.name())

                        // Album creation requires artist role; membership is checked in the use case.
                        .requestMatchers(HttpMethod.POST, "/api/v1/albums").hasAnyRole(Role.ARTIST.name(), Role.ADMIN.name())

                        // Endpoints for any authenticated user
                        .requestMatchers("/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/albums/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/me/likes/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/me/likes/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/playlists").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/playlists/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/playlists/**").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/playlists/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/playlists/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/playlists/**").authenticated()
                       .requestMatchers(HttpMethod.GET, "/api/v1/artists").authenticated()
                       .requestMatchers(HttpMethod.GET, "/api/v1/artists/*/albums").authenticated()
                        .requestMatchers("/api/v1/me/playlists").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/songs/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/artists/**").authenticated()

                        // All other requests must be authenticated
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        final DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(final AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Argon2id — modern memory-hard hashing (PHC winner). Defaults for Spring Security 5.8+:
        // salt 16 bytes, hash 32 bytes, parallelism 1, memory 16 MB, iterations 2.
        // Swapping algorithms only requires changing this bean (the core depends on the
        // PasswordHasher port, implemented by SpringSecurityPasswordHasher).
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
