package com.spotpobre.backend.domain.common;

/**
 * Thrown when an uploaded object fails integrity verification (size, content type
 * or checksum mismatch against the expected values recorded at initiation).
 */
public class UploadIntegrityException extends RuntimeException {

    public UploadIntegrityException(final String message) {
        super(message);
    }
}
