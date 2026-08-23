package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.user.port.in.AuthenticateUserUseCase;
import com.spotpobre.backend.application.user.port.in.RegisterUserIdempotentlyUseCase;
import com.spotpobre.backend.application.user.port.in.RegisterUserIdempotentlyUseCase.RegistrationOutcome;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.infrastructure.security.service.JwtService;
import com.spotpobre.backend.infrastructure.web.dto.request.AuthenticationRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.RegisterRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.AuthenticationResponse;
import com.spotpobre.backend.infrastructure.web.mapper.AuthApiMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final RegisterUserIdempotentlyUseCase registerUserIdempotentlyUseCase;
    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final JwtService jwtService;
    private final AuthApiMapper mapper;

    /**
     * Registration requires a durable {@code Idempotency-Key} (spec §4.3). A missing or invalid
     * key is rejected with 400; retries with the same key resolve the same user and mint a fresh
     * JWT, flagged with the {@code Idempotency-Replayed} response header.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @RequestHeader(value = "Idempotency-Key", required = false) final String idempotencyKey,
            @RequestBody @Valid final RegisterRequest request) {
        final var command = mapper.toCommand(request);
        final RegistrationOutcome outcome =
                registerUserIdempotentlyUseCase.registerIdempotently(idempotencyKey, command);

        // Original success status is preserved on replay (registration responds 200).
        return ResponseEntity.ok()
                .header("Idempotency-Replayed", String.valueOf(outcome.replayed()))
                .body(toResponse(outcome.user()));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(@RequestBody @Valid final AuthenticationRequest request) {
        final var command = mapper.toCommand(request);
        final User authenticatedUser = authenticateUserUseCase.authenticate(command);

        return ResponseEntity.ok(toResponse(authenticatedUser));
    }

    private AuthenticationResponse toResponse(final User user) {
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toList());

        final UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getProfile().email(),
                user.getPassword(),
                authorities
        );

        final String token = jwtService.generateToken(userDetails);
        return new AuthenticationResponse(token);
    }
}
