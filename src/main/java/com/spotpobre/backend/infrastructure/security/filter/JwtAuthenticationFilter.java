package com.spotpobre.backend.infrastructure.security.filter;

import com.spotpobre.backend.infrastructure.security.adapter.CachedUserDetails;
import com.spotpobre.backend.infrastructure.security.service.JwtService;
import com.spotpobre.backend.infrastructure.web.exception.RestErrorResponseWriter;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RestErrorResponseWriter errorResponseWriter;

    @Override
    protected void doFilterInternal(
            @NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            final String userEmail = jwtService.extractUsername(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                final UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                // Password-change revocation gate (#13): tokens issued BEFORE the last password
                // change are rejected even though their signature is still valid.
                if (issuedBeforePasswordChange(jwt, userDetails)) {
                    errorResponseWriter.write(request, response,
                            org.springframework.http.HttpStatus.UNAUTHORIZED,
                            "Unauthorized", "Invalid or expired token");
                    return;
                }
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    final UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "Invalid or expired token"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean issuedBeforePasswordChange(final String jwt, final UserDetails userDetails) {
        if (!(userDetails instanceof CachedUserDetails cached)
                || cached.getPasswordChangedAt() == null) {
            return false;
        }
        final java.util.Date issuedAt = jwtService.extractClaim(jwt, io.jsonwebtoken.Claims::getIssuedAt);
        return issuedAt != null
                && issuedAt.toInstant().isBefore(cached.getPasswordChangedAt());
    }
}