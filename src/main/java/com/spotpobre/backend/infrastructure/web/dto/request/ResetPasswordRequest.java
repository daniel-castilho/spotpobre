package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank(message = "Token cannot be blank")
        @Size(max = 256, message = "Token cannot exceed 256 characters")
        String token,

        @NotBlank(message = "Password cannot be blank")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters long")
        @Pattern(regexp = "^[^\\p{Cntrl}]+$", message = "Password cannot contain control characters")
        String newPassword
) {
}
