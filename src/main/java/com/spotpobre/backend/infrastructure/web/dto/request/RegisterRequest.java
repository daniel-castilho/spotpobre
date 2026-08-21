package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Name cannot be blank")
        @Size(max = 100, message = "Name cannot exceed 100 characters")
        @Pattern(regexp = "^[^\\p{Cntrl}]+$", message = "Name cannot contain control characters")
        String name,

        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Invalid email format")
        @Size(max = 320, message = "Email cannot exceed 320 characters")
        String email,

        // Password is never trimmed or normalized — validated as received.
        @NotBlank(message = "Password cannot be blank")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters long")
        String password,

        @NotBlank(message = "Country cannot be blank")
        @Pattern(regexp = "[A-Z]{2}", message = "Country must be a two-letter uppercase ISO code")
        String country
) {
}
