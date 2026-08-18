package com.spotpobre.backend.infrastructure.web.dto.response;

public record PresignedUploadPartResponse(int partNumber, String url) {
}
