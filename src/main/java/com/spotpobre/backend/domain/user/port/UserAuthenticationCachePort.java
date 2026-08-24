package com.spotpobre.backend.domain.user.port;

/**
 * Invalidates cached authentication material for one identity (defect #13): after a password
 * change the cached credentials must be dropped so the next request re-reads the store.
 */
public interface UserAuthenticationCachePort {

    void evict(String email);
}
