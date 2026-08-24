package com.spotpobre.backend.domain.user.port;

import com.spotpobre.backend.domain.user.model.AccountToken;
import com.spotpobre.backend.domain.user.model.AccountTokenPurpose;
import com.spotpobre.backend.domain.user.model.UserId;

import java.util.Optional;

public interface AccountTokenRepository {

    void save(final AccountToken token);

    /**
     * @return the stored token for the given hash and purpose, provided it was never redeemed.
     * Expired rows disappear through DynamoDB TTL; consumers still double-check
     * {@code AccountToken.isExpiredAt(now)} against the current clock.
     */
    Optional<AccountToken> findActiveByHash(final String tokenHash, final AccountTokenPurpose purpose);

    /** Flags the token as redeemed so replays of the same link are rejected. */
    void markUsed(final String tokenHash);

    /**
     * Burns every still-unused token of one user and purpose (defect #13): after a successful
     * password reset no sibling link may remain redeemable. Best-effort per row; racing
     * redemptions are resolved by the conditional used-flag write.
     *
     * @return how many sibling tokens were burned by this call
     */
    int markAllUsedForUser(UserId userId, AccountTokenPurpose purpose,
                           java.time.Instant at);
}
