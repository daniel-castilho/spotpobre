package com.spotpobre.backend.infrastructure.security.ratelimit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Encodes rate-limit subject keys as HMAC-SHA256 digests (spec section 8.2): raw e-mails and
 * IPs are never stored in Redis. The secret is deployment-specific (env RATE_LIMIT_KEY_SECRET)
 * and independent from the JWT signing secret.
 */
public final class RateLimitKeyEncoder {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public RateLimitKeyEncoder(final String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                    "rate-limit key secret is required (RATE_LIMIT_KEY_SECRET)");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param scope   bucket discriminator, e.g. {@code register:ip} or {@code upload:user-album}
     * @param subject raw identity material (already normalized by the caller)
     * @return {@code rl:<scope>:<hmac-sha256-hex>}
     */
    public String encode(final String scope, final String subject) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            mac.update(scope.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) 0x1f);
            byte[] digest = mac.doFinal(subject.getBytes(StandardCharsets.UTF_8));
            return "rl:" + scope + ":" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM without HmacSHA256 support", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode rate-limit key", e);
        }
    }

    /** Low-cardinality metric/fallback digest when the subject must not appear anywhere. */
    public static String sha256Hex(final String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM without SHA-256 support", e);
        }
    }
}
