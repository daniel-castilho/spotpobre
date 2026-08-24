package com.spotpobre.backend.application.user;

import com.spotpobre.backend.domain.user.model.AccountToken;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EmailVerificationSettingsTest {

    private static final Duration VERIFICATION_TTL = Duration.ofHours(24);

    @Test
    void positiveTtlIsPreserved() {
        assertEquals(Duration.ofMinutes(42),
                new EmailVerificationSettings(Duration.ofMinutes(42)).verificationTtl());
    }

    @Test
    void nullNegativeOrZeroFallBackToDevSafeDefault() {
        assertEquals(VERIFICATION_TTL, new EmailVerificationSettings(null).verificationTtl());
        assertEquals(VERIFICATION_TTL, new EmailVerificationSettings(Duration.ofSeconds(-1)).verificationTtl());
        assertEquals(VERIFICATION_TTL, new EmailVerificationSettings(Duration.ZERO).verificationTtl());
    }

    @Test
    void verificationTtlStaysSeparateFromPasswordRecoveryTtl() {
        // Binding decision v0.12.0: verification tokens live 24h — deliberately separate
        // from the 30-minute password-recovery TTL.
        assertEquals(Duration.ofHours(24), new EmailVerificationSettings(null).verificationTtl());
        assertNotEquals(AccountToken.DEFAULT_TTL, new EmailVerificationSettings(null).verificationTtl());
    }
}
