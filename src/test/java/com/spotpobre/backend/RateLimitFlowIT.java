package com.spotpobre.backend;

import com.spotpobre.backend.infrastructure.web.dto.request.AuthenticationRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "rate-limit.enabled=true",
        "rate-limit.limit=3",
        // Wall-clock-aligned fixed window: a 1m window rolls over mid-test when the suite's
        // timing shifts, resetting the counter (4th request → 401 instead of 429). A 1h window
        // cannot roll over during a single test method.
        "rate-limit.window=1h"
})
class RateLimitFlowIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void authenticate_exceedingLimit_shouldReturn429() {
        String email = "ratelimit-" + UUID.randomUUID() + "@example.com";
        AuthenticationRequest request = new AuthenticationRequest(email, "wrong-password");

        for (int i = 0; i < 3; i++) {
            given()
                    .header("X-Forwarded-For", "203.0.113.10")
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/auth/authenticate")
                    .then()
                    .statusCode(401);
        }

        given()
                .header("X-Forwarded-For", "203.0.113.10")
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/auth/authenticate")
                .then()
                .statusCode(429)
                .body("error", equalTo("Too Many Requests"));
    }

    @Test
    void authenticate_withinLimit_shouldNotBeThrottled() {
        String email = "ratelimit-ok-" + UUID.randomUUID() + "@example.com";
        AuthenticationRequest request = new AuthenticationRequest(email, "wrong-password");

        for (int i = 0; i < 2; i++) {
            given()
                    .header("X-Forwarded-For", "203.0.113.11")
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/auth/authenticate")
                    .then()
                    .statusCode(401);
        }
    }
}