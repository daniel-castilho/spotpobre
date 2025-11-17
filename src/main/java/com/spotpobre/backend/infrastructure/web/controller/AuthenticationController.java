package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.user.port.in.AuthenticateUserUseCase;
import com.spotpobre.backend.application.user.port.in.RegisterUserUseCase;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final RegisterUserUseCase registerUserUseCase;
    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final JwtService jwtService;
    private final AuthApiMapper mapper;

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(@RequestBody @Valid final RegisterRequest request) {
        final var command = mapper.toCommand(request);
        final User registeredUser = registerUserUseCase.registerUser(command);

        var authorities = registeredUser.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .collect(Collectors.toList());

        final UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                registeredUser.getProfile().email(),
                registeredUser.getPassword(),
                authorities
        );

        final String token = jwtService.generateToken(userDetails);
        return ResponseEntity.ok(new AuthenticationResponse(token));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(@RequestBody @Valid final AuthenticationRequest request) {
        final var command = mapper.toCommand(request);
        final User authenticatedUser = authenticateUserUseCase.authenticate(command);

        var authorities = authenticatedUser.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .collect(Collectors.toList());

        final UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                authenticatedUser.getProfile().email(),
                authenticatedUser.getPassword(),
                authorities
        );

        final String token = jwtService.generateToken(userDetails);
        return ResponseEntity.ok(new AuthenticationResponse(token));
    }
}
