package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompletedUploadPartRequest(
        @Min(value = 1, message = "Part number must be at least 1")
        @Max(value = 10000, message = "Part number cannot exceed 10000")
        int partNumber,

        @NotBlank(message = "Part ETag cannot be blank")
        @Size(max = 256, message = "Part ETag cannot exceed 256 characters")
        @Pattern(regexp = "^[A-Za-z0-9\"/-]+$", message = "Part ETag contains invalid characters")
        String eTag
) {
}
