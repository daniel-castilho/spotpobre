package com.spotpobre.backend.application.user;

import java.time.Duration;

/**
 * Application-owned view of the e-mail verification policy values the user feature needs.
 *
 * <p>Translated explicitly from the infrastructure configuration properties by an adapter
 * bean so that application services never depend on infrastructure configuration types.</p>
 *
 * @param verificationTtl lifetime of e-mail-verification tokens (deliberately separate from
 *                        the 30-minute password-recovery TTL).
 */
public record EmailVerificationSettings(Duration verificationTtl) {

    /** Mirrors the dev-safe default of the infrastructure e-mail contract. */
    static final Duration DEFAULT_TTL = Duration.ofHours(24);

    public EmailVerificationSettings {
        if (verificationTtl == null || verificationTtl.isNegative() || verificationTtl.isZero()) {
            verificationTtl = DEFAULT_TTL;
        }
    }
}
