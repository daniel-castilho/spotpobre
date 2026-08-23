package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmEmailVerificationRequest(

        @NotBlank(message = "Token cannot be blank")
        @Size(max = 256, message = "Token cannot exceed 256 characters")
        String token
) {
}
