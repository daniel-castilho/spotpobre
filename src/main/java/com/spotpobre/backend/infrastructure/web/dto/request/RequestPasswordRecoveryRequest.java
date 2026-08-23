package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestPasswordRecoveryRequest(

        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Invalid email format")
        @Size(max = 320, message = "Email cannot exceed 320 characters")
        String email
) {
}
