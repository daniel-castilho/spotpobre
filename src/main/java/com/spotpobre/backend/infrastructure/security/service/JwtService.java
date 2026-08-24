package com.spotpobre.backend.infrastructure.security.service;

import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.port.AuthTokenIssuer;
import com.spotpobre.backend.infrastructure.config.properties.JwtProperties; // Import JwtProperties
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JwtService implements AuthTokenIssuer {

    private final JwtProperties jwtProperties; // Inject JwtProperties

    public JwtService(JwtProperties jwtProperties) { // Constructor injection
        this.jwtProperties = jwtProperties;
    }

    public String extractUsername(final String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(final String token, final Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(final UserDetails userDetails) {
        final Map<String, Object> claims = new HashMap<>();
        claims.put("authorities", userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.expiration().toMillis())) // Use expiration from properties
                .signWith(getSigningKey())
                .compact();
    }

    /** {@inheritDoc} — translates the domain user to the token subject and role claims. */
    @Override
    public String issueFor(final User user) {
        final var authorities = user.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toList());
        final UserDetails details = org.springframework.security.core.userdetails.User.builder()
                .username(user.getProfile().email())
                .password(user.getPassword())
                .authorities(authorities)
                .build();
        return generateToken(details);
    }

    public boolean isTokenValid(final String token, final UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(final String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(final String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(final String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8)); // Use secret from properties
    }
}
