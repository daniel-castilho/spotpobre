package com.spotpobre.backend.infrastructure.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ConfirmSongUploadRequest(
        @NotBlank(message = "Storage key cannot be blank")
        String storageKey,

        String multipartUploadId,

        @Valid
        List<CompletedUploadPartRequest> parts
) {
}
