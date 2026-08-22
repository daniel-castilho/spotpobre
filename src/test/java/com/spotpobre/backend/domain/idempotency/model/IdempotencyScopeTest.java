package com.spotpobre.backend.domain.idempotency.model;

import com.spotpobre.backend.domain.common.IdempotencyKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyScopeTest {

    private IdempotencyKey key() {
        return IdempotencyKey.of("0123456789abcdef");
    }

    @Test
    void scopeKey_isDeterministicForIdenticalInputs() {
        IdempotencyScope first = new IdempotencyScope("v1", "user:u-1", "POST", "/api/v1/playlists", "", key());
        IdempotencyScope second = new IdempotencyScope("v1", "user:u-1", "POST", "/api/v1/playlists", "", key());

        assertEquals(first.scopeKey(), second.scopeKey());
        assertEquals(64, first.scopeKey().length(), "persisted scope key is SHA-256 hex");
    }

    @Test
    void scopeKey_differsWhenAnyScopeInputDiffers() {
        IdempotencyScope base = new IdempotencyScope("v1", "user:u-1", "POST", "/api/v1/playlists", "", key());

        assertNotEquals(base.scopeKey(),
                new IdempotencyScope("v1", "user:u-2", "POST", "/api/v1/playlists", "", key()).scopeKey());
        assertNotEquals(base.scopeKey(),
                new IdempotencyScope("v1", "user:u-1", "PUT", "/api/v1/playlists", "", key()).scopeKey());
        assertNotEquals(base.scopeKey(),
                new IdempotencyScope("v1", "user:u-1", "POST", "/api/v1/songs", "", key()).scopeKey());
        assertNotEquals(base.scopeKey(),
                new IdempotencyScope("v1", "user:u-1", "POST", "/api/v1/playlists", "", IdempotencyKey.of("fedcba9876543210")).scopeKey());
    }

    @Test
    void anonymousRegistration_usesFixedActorScopeWithoutUserOrNetworkIdentity() {
        IdempotencyScope anonymous = IdempotencyScope.anonymousRegistration(
                "v1", "POST", "/api/v1/users/register", key());

        assertEquals("anonymous-registration", anonymous.actorScope());
        assertEquals("", anonymous.pathIdentity());
        assertEquals(new IdempotencyScope("v1", "anonymous-registration", "POST",
                "/api/v1/users/register", "", key()).scopeKey(), anonymous.scopeKey());
    }

    @Test
    void correlationDigest_neverContainsTheRawKey() {
        IdempotencyScope scope = new IdempotencyScope(
                "v1", "user:u-1", "POST", "/api/v1/playlists", "", IdempotencyKey.of("0123456789abcdef"));

        String digest = scope.correlationDigest();

        assertTrue(digest.length() <= 16);
        assertTrue(!digest.contains(scope.key().value()));
        assertTrue(!scope.scopeKey().contains(scope.key().value()),
                "even the full scope key must not embed the raw client key");
    }
}
