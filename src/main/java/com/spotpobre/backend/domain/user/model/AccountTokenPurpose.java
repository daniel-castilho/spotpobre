package com.spotpobre.backend.domain.user.model;

/**
 * Lifecycle flow an {@link AccountToken} may redeem. Purposes are isolated: a token issued for
 * one flow can never be redeemed by another.
 */
public enum AccountTokenPurpose {
    PASSWORD_RESET,
    EMAIL_VERIFICATION
}
