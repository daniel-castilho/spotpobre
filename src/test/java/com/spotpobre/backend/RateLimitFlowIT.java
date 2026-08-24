package com.spotpobre.backend;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * E2E proof of the register rate-limit policy (spec section 8.3): tiny configured capacity,
 * canonical 429 envelope with RateLimit-* + Retry-After headers, and per-identity bucket
 * isolation through the trusted-proxy resolver. Full application with real Redis + LocalStack.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitFlowIT {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    static {
        AbstractIntegrationTest.provisionLocalStack();
        REDIS.start();
    }

    @LocalServerPort
    private int port;

    @org.springframework.test.context.DynamicPropertySource
    static void registerProperties(final org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("aws.dynamodb.endpoint", AbstractIntegrationTest::localstackEndpoint);
        registry.add("aws.s3.endpoint", AbstractIntegrationTest::localstackEndpoint);
        registry.add("aws.credentials.access-key", () -> "test");
        registry.add("aws.credentials.secret-key", () -> "test");
        registry.add("aws.region", AbstractIntegrationTest::localstackRegion);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // Tiny policies so the IT never needs long sleeps (spec section 8.5).
        registry.add("rate-limit.key-secret", () -> "it-rate-limit-secret");
        registry.add("rate-limit.register-ip-capacity", () -> "3");
        registry.add("rate-limit.authenticate-ip-capacity", () -> "50");
        registry.add("rate-limit.trusted-proxy-cidrs", () -> "127.0.0.0/8,::1/128");
    }

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    @Test
    void registerPolicy_blocksAfterCapacity_withCanonical429AndHeaders() {
        // Unique trusted-resolved identity (XFF first entry; the loopback peer is trusted).
        String identity = "198.51.100." + (1 + Math.abs(UUID.randomUUID().hashCode()) % 200);

        given()
                .header("X-Forwarded-For", identity)
                .header("Idempotency-Key", idemKey())
                .contentType(ContentType.JSON)
                .body(registerBody(identity, "a"))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .header("RateLimit-Limit", Matchers.equalTo("3"))
                .header("RateLimit-Remaining", Matchers.notNullValue())
                .header("RateLimit-Reset", Matchers.notNullValue());

        for (int i = 0; i < 2; i++) {
            given()
                    .header("X-Forwarded-For", identity)
                    .header("Idempotency-Key", idemKey())
                    .contentType(ContentType.JSON)
                    .body(registerBody(identity, "b" + i))
                    .when()
                    .post("/api/v1/auth/register")
                    .then()
                    .statusCode(org.hamcrest.Matchers.anyOf(Matchers.is(200), Matchers.is(409)));
        }

        // IP-wide capacity exhausted: canonical 429 envelope with Retry-After.
        given()
                .header("X-Forwarded-For", identity)
                .header("Idempotency-Key", idemKey())
                .contentType(ContentType.JSON)
                .body(registerBody(identity, "z"))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(429)
                .header("RateLimit-Limit", Matchers.equalTo("3"))
                .header("Retry-After", Matchers.notNullValue());
    }

    @Test
    void distinctForwardedIdentities_haveIndependentBuckets() {
        String a = "198.51.100." + (1 + Math.abs(UUID.randomUUID().hashCode()) % 200);
        String b = "198.51.100." + (1 + Math.abs(UUID.randomUUID().hashCode()) % 200);

        // Exhaust A's whole IP-wide bucket (capacity 3).
        for (int i = 0; i < 3; i++) {
            given()
                    .header("X-Forwarded-For", a)
                    .header("Idempotency-Key", idemKey())
                    .contentType(ContentType.JSON)
                    .body(registerBody(a, "n" + i))
                    .when()
                    .post("/api/v1/auth/register")
                    .then()
                    .statusCode(org.hamcrest.Matchers.anyOf(Matchers.is(200),
                            Matchers.is(409)));
        }
        given()
                .header("X-Forwarded-For", a)
                .header("Idempotency-Key", idemKey())
                .contentType(ContentType.JSON)
                .body(registerBody(a, "overflow"))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(429);

        // B's budget must be untouched by A's exhaustion.
        given()
                .header("X-Forwarded-For", b)
                .header("Idempotency-Key", idemKey())
                .contentType(ContentType.JSON)
                .body(registerBody(b, "fresh"))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(200)
                .header("RateLimit-Limit", Matchers.equalTo("3"));
    }

    private static String idemKey() {
        return "rl-it-" + UUID.randomUUID();
    }

    private static String registerBody(final String identitySeed, final String suffix) {
        return String.format(
                "{\"name\":\"RL\",\"email\":\"rl-%s-%s@example.com\","
                        + "\"password\":\"password123\",\"country\":\"US\"}",
                identitySeed.replace('.', '-'), suffix);
    }
}
