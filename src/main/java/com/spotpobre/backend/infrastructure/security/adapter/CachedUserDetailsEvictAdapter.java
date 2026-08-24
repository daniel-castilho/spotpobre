package com.spotpobre.backend.infrastructure.security.adapter;

import com.spotpobre.backend.domain.user.port.UserAuthenticationCachePort;
import com.spotpobre.backend.infrastructure.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Cache-backed adapter: evicts the user-details entry so post-password-change requests
 * re-read the store instead of trusting stale cached credentials.
 */
@Component
@RequiredArgsConstructor
public class CachedUserDetailsEvictAdapter implements UserAuthenticationCachePort {

    private final CacheManager cacheManager;

    @Override
    public void evict(final String email) {
        var cache = cacheManager.getCache(CacheConfig.USER_CACHE);
        if (cache != null) {
            cache.evictIfPresent(email);
        }
    }
}
