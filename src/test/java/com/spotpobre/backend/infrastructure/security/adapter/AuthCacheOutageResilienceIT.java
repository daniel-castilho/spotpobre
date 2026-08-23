package com.spotpobre.backend.infrastructure.security.adapter;

import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.UserRepository;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the S6 degradation policy end to end: with the Redis-backed {@code userCache} active,
 * killing the Redis container mid-flight must NOT break authentication lookups — the outage is
 * treated as a cache miss and {@link UserDetailsServiceImpl} falls through to the source of truth
 * (DynamoDB). Regression test for the AGENTS.md debt item "auth cache has no Redis-outage
 * fallback".
 *
 * <p>Self-contained on purpose: {@code AbstractIntegrationTest} pins {@code spring.cache.type=simple}
 * for the whole suite, while this scenario requires the real Redis cache manager.</p>
 */
@SpringBootTest
class AuthCacheOutageResilienceIT {

    private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.2"))
            .withServices("s3", "dynamodb")
            .waitingFor(Wait.forHttp("/_localstack/health").forStatusCode(200));

    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    static {
        // Singletons for the whole JVM so Spring's cached context never outlives them.
        LOCALSTACK.start();
        REDIS.start();
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DynamoDbEnhancedClient enhancedClient;

    @Autowired
    private TableSchema<UserDocument> userTableSchema;

    @org.springframework.test.context.DynamicPropertySource
    static void registerProperties(final org.springframework.test.context.DynamicPropertyRegistry registry) {
        // LocalStack container endpoints + credentials (mirrors AbstractIntegrationTest).
        registry.add("aws.dynamodb.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("aws.s3.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("aws.credentials.access-key", () -> "test");
        registry.add("aws.credentials.secret-key", () -> "test");
        registry.add("aws.region", LOCALSTACK::getRegion);
        // Real Redis-backed cache manager — this is exactly what the outage scenario requires.
        registry.add("spring.cache.type", () -> "redis");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> String.valueOf(REDIS.getMappedPort(6379)));
    }

    @Test
    void authLookups_surviveRedisOutageByDegradingToSource() {
        provisionUsersTable();
        String email = "outage-" + UUID.randomUUID() + "@example.com";
        userRepository.save(User.createWithLocalPassword(
                new UserProfile("Outage User", email, "BR"), "password123"));

        // First lookup populates the Redis cache through the source of truth.
        UserDetails warmed = userDetailsService.loadUserByUsername(email);
        assertNotNull(warmed);

        // The cache manager must actually be serving this cache from Redis.
        Set<String> cachedKeys = redisTemplate.keys("userCache::*");
        assertNotNull(cachedKeys);
        assertFalse(cachedKeys.isEmpty(), "userCache entries must live in Redis before the outage");

        // Second lookup is served from the cached entry.
        assertEquals(warmed.getUsername(), userDetailsService.loadUserByUsername(email).getUsername());

        // Simulate the outage: kill Redis entirely, then keep authenticating.
        REDIS.stop();

        UserDetails degraded = userDetailsService.loadUserByUsername(email);
        assertNotNull(degraded, "a Redis outage must degrade to source lookup, not fail auth");
        assertEquals(email, degraded.getUsername());
        assertTrue(degraded.getAuthorities().stream()
                .anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
    }

    private void provisionUsersTable() {
        final DynamoDbTable<UserDocument> users = enhancedClient.table("Users", userTableSchema);
        try {
            users.createTable();
        } catch (ResourceInUseException e) {
            // Table already exists from a previous context — fine.
        }
    }
}
