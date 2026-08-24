package com.spotpobre.backend.domain.user.port;

/**
 * Race-safe, bounded resend cooldown backed by the shared rate-limit authority (spec §8.3):
 * replicas converge on one window instead of per-instance drift.
 */
public interface VerificationResendCooldownPort {

    /**
     * Attempts to reserve one send slot for {@code subjectKey} (already pseudonymized by the
     * adapter layer).
     *
     * @return {@code true} when the send is within the cooldown budget
     */
    boolean tryAcquire(String subjectKey);
}
