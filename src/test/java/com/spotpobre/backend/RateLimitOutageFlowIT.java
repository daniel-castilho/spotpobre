package com.spotpobre.backend;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * Backend-outage behaviour of the rate-limit authority (spec section 8.3): abuse-sensitive
 * flows fail CLOSED with the canonical 503 envelope and must never claim the caller exceeded
 * a limit. Redis is deliberately unreachable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1", // closed port: instant connection refusal
                "spring.cache.type=simple",
                "rate-limit.key-secret=outage-it-secret"
        })
class RateLimitOutageFlowIT {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    @Test
    void register_withRedisDown_failsClosedWithCanonical503NotClaimingLimit() {
        given()
                .header("X-Forwarded-For", "198.51.100." + (1 + Math.abs(UUID.randomUUID().hashCode()) % 200))
                .header("Idempotency-Key", "outage-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body("{\"name\":\"RL\",\"email\":\"outage-" + UUID.randomUUID()
                        + "@example.com\",\"password\":\"password123\",\"country\":\"US\"}")
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(503)
                .body("status", equalTo(503))
                .body("message", containsString("unavailable"))
                .body("message", not(containsString("Rate limit exceeded")));
    }
}
