package com.spotpobre.backend.domain.idempotency.model;

import com.spotpobre.backend.domain.common.Digests;
import com.spotpobre.backend.domain.common.IdempotencyKey;

import java.util.Objects;

/**
 * Scope of an idempotent operation: everything that makes two requests "the same logical
 * operation" for retry purposes. Only the SHA-256 digest ({@link #scopeKey()}) is persisted —
 * raw keys, e-mails and IPs never reach the store or logs.
 *
 * <p>Authenticated scope input: {@code apiVersion | user:<uuid> | method | routeTemplate |
 * pathIdentity | key}. Anonymous registration scope input: {@code apiVersion |
 * anonymous-registration | method | routeTemplate | key}.</p>
 *
 * <p>Pure Java — no framework types.</p>
 */
public record IdempotencyScope(
        String apiVersion,
        String actorScope,
        String method,
        String routeTemplate,
        String pathIdentity,
        IdempotencyKey key
) {

    public IdempotencyScope {
        Objects.requireNonNull(apiVersion, "apiVersion is required");
        Objects.requireNonNull(actorScope, "actorScope is required");
        Objects.requireNonNull(method, "method is required");
        Objects.requireNonNull(routeTemplate, "routeTemplate is required");
        Objects.requireNonNull(key, "key is required");
        if (pathIdentity == null) {
            pathIdentity = "";
        }
    }

    public static IdempotencyScope anonymousRegistration(
            final String apiVersion, final String method, final String routeTemplate, final IdempotencyKey key) {
        return new IdempotencyScope(apiVersion, "anonymous-registration", method, routeTemplate, "", key);
    }

    /**
     * @return the persisted scope key: SHA-256 hex over the canonical pipe-joined scope inputs.
     */
    public String scopeKey() {
        return Digests.sha256Hex(String.join("|",
                apiVersion, actorScope, method, routeTemplate, pathIdentity, key.value()));
    }

    /**
     * @return a safe short correlation digest of the scope for logs/metrics (never the raw key).
     */
    public String correlationDigest() {
        return Digests.shortDigest(scopeKey());
    }
}
