package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.user.port.in.AuthenticateUserUseCase;
import com.spotpobre.backend.application.user.port.in.ConfirmEmailVerificationUseCase;
import com.spotpobre.backend.application.user.port.in.GetCurrentUserUseCase;
import com.spotpobre.backend.application.user.port.in.RegisterUserIdempotentlyUseCase;
import com.spotpobre.backend.application.user.port.in.RegisterUserIdempotentlyUseCase.RegistrationOutcome;
import com.spotpobre.backend.application.user.port.in.RequestEmailVerificationResendUseCase;
import com.spotpobre.backend.application.user.port.in.RequestPasswordRecoveryUseCase;
import com.spotpobre.backend.application.user.port.in.ResetPasswordUseCase;
import com.spotpobre.backend.infrastructure.web.dto.request.AuthenticationRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.ConfirmEmailVerificationRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.RegisterRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.RequestPasswordRecoveryRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.ResetPasswordRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.AuthenticationResponse;
import com.spotpobre.backend.infrastructure.web.mapper.AuthApiMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final RegisterUserIdempotentlyUseCase registerUserIdempotentlyUseCase;
    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final RequestPasswordRecoveryUseCase requestPasswordRecoveryUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final RequestEmailVerificationResendUseCase requestEmailVerificationResendUseCase;
    private final ConfirmEmailVerificationUseCase confirmEmailVerificationUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
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
                .body(new AuthenticationResponse(outcome.token()));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(@RequestBody @Valid final AuthenticationRequest request) {
        final var command = mapper.toCommand(request);
        final AuthenticateUserUseCase.AuthenticatedSession session = authenticateUserUseCase.authenticate(command);

        return ResponseEntity.ok(new AuthenticationResponse(session.token()));
    }

    /**
     * Always acknowledges 202 regardless of e-mail existence: the response must not leak which
     * addresses have accounts. Delivery failures never change the answer.
     */
    @PostMapping("/password/recover")
    public ResponseEntity<Void> requestPasswordRecovery(
            @RequestBody @Valid final RequestPasswordRecoveryRequest request) {
        requestPasswordRecoveryUseCase.requestRecovery(
                new RequestPasswordRecoveryUseCase.RequestPasswordRecoveryCommand(request.email()));
        return ResponseEntity.accepted().build();
    }

    /** Redeems a single-use recovery token and replaces the account password. */
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(
            @RequestBody @Valid final ResetPasswordRequest request) {
        resetPasswordUseCase.resetPassword(
                new ResetPasswordUseCase.ResetPasswordCommand(request.token(), request.newPassword()));
        return ResponseEntity.noContent().build();
    }

    /**
     * Authenticated resend of the verification e-mail. Always 202: an already-verified address
     * skips the send silently, a per-user cooldown answers 429, and no extra verification state
     * is revealed beyond {@code emailVerified} on the profile response.
     */
    @PostMapping("/email/verification/resend")
    public ResponseEntity<Void> resendEmailVerification(final java.security.Principal principal) {
        final var userId = getCurrentUserUseCase.getCurrentUserId(principal.getName());
        requestEmailVerificationResendUseCase.requestResend(
                new RequestEmailVerificationResendUseCase.RequestEmailVerificationResendCommand(userId));
        return ResponseEntity.accepted().build();
    }

    /** Redeems a single-use verification token (anonymous — the token is the proof). */
    @PostMapping("/email/verification/confirm")
    public ResponseEntity<Void> confirmEmailVerification(
            @RequestBody @Valid final ConfirmEmailVerificationRequest request) {
        confirmEmailVerificationUseCase.confirm(
                new ConfirmEmailVerificationUseCase.ConfirmEmailVerificationCommand(request.token()));
        return ResponseEntity.noContent().build();
    }
}
