package com.spotpobre.backend.domain.user.port;

import com.spotpobre.backend.domain.user.model.User;

/**
 * Outbound port for minting a signed authentication token for an authenticated user.
 *
 * <p>The application layer depends only on this contract — never on a concrete token
 * technology — so JWT signing (or any future mechanism) can be swapped by exchanging the
 * adapter. Controllers consume tokens exclusively through application use-case results,
 * never through infrastructure services.</p>
 */
public interface AuthTokenIssuer {

    /**
     * Mints a token whose subject identifies {@code user} and whose claims carry the
     * account's roles.
     */
    String issueFor(User user);
}
