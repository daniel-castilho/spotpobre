package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CompletedUploadPartRequest(
        @Min(value = 1, message = "Part number must be at least 1")
        int partNumber,

        @NotBlank(message = "Part ETag cannot be blank")
        String eTag
) {
}
