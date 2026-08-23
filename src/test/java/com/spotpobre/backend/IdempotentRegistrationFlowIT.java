package com.spotpobre.backend;

import com.spotpobre.backend.infrastructure.web.dto.request.RegisterRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E protocol matrix for the idempotency-protected registration endpoint (step 6A).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotentRegistrationFlowIT extends AbstractFlowIT {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    private RegisterRequest body(final String email) {
        return new RegisterRequest("Idempotent Registration IT", email, "password123", "US");
    }

    private String uniqueEmail() {
        return "idempotent-registration-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void missingKey_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body(body(uniqueEmail()))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(400);
    }

    @Test
    void invalidKey_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "short")
                .body(body(uniqueEmail()))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(400);

        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "has space in key 1234")
                .body(body(uniqueEmail()))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(400);
    }

    @Test
    void sameKeySameRequest_secondExecutionReplaysSameUserWithFreshToken() {
        final String key = "reg-flow-" + UUID.randomUUID();
        final String email = uniqueEmail();

        // First execution: original response.
        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", key)
                .body(body(email))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(200)
                .header("Idempotency-Replayed", "false");

        // Retry with the same key and body: replay, same user, fresh token.
        final String replayedToken = given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", key)
                .body(body(email))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(200)
                .header("Idempotency-Replayed", "true")
                .body("token", org.hamcrest.Matchers.notNullValue())
                .extract()
                .path("token");

        // The fresh replay token is usable — the account exists exactly once.
        given()
                .header("Authorization", "Bearer " + replayedToken)
                .when()
                .get("/api/v1/users/me")
                .then()
                .statusCode(200)
                .body("email", org.hamcrest.Matchers.equalTo(email));
    }

    @Test
    void sameKeyDifferentRequest_returns409() {
        final String key = "reg-flow-" + UUID.randomUUID();

        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", key)
                .body(body(uniqueEmail()))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(200);

        final RegisterRequest different = new RegisterRequest(
                "Different Name", uniqueEmail(), "password123", "PT");

        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", key)
                .body(different)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(409);
    }

    @Test
    void concurrentDuplicates_sameKey_exactlyOneOriginalExecution() throws Exception {
        final String key = "reg-flow-race-" + UUID.randomUUID();
        final String email = uniqueEmail();
        int clients = 6;

        record Attempt(int status, String replayedHeader, String retryAfterHeader) {
        }

        ExecutorService pool = Executors.newFixedThreadPool(clients);
        try {
            List<Future<Attempt>> attempts = new ArrayList<>();
            for (int i = 0; i < clients; i++) {
                attempts.add(pool.submit((Callable<Attempt>) () -> {
                    io.restassured.response.Response response = given()
                            .contentType(ContentType.JSON)
                            .header("Idempotency-Key", key)
                            .body(body(email))
                            .when()
                            .post("/api/v1/auth/register");
                    return new Attempt(
                            response.statusCode(),
                            response.header("Idempotency-Replayed"),
                            response.header("Retry-After"));
                }));
            }

            int originals = 0;
            for (int i = 0; i < clients; i++) {
                Attempt attempt = attempts.get(i).get();
                if (attempt.status() == 200) {
                    assertTrue("true".equals(attempt.replayedHeader())
                                    || "false".equals(attempt.replayedHeader()),
                            "successes carry the Idempotency-Replayed header");
                    if ("false".equals(attempt.replayedHeader())) {
                        originals++;
                    }
                } else if (attempt.status() == 409) {
                    // In-progress conflict while the winning lease was still live.
                    assertTrue(attempt.retryAfterHeader() != null,
                            "in-progress conflicts carry Retry-After");
                } else {
                    throw new AssertionError("unexpected status " + attempt.status());
                }
            }
            assertEquals(1, originals,
                    "exactly one execution creates the resource; the rest replay or conflict");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void differentKeysSameEmail_normalBusinessConflictStillApplies() {
        final String email = uniqueEmail();

        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "reg-flow-" + UUID.randomUUID())
                .body(body(email))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "reg-flow-" + UUID.randomUUID())
                .body(body(email))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(409);
    }

    @Test
    void authenticateEndpoint_remainsFreeOfIdempotencyContract() {
        final String email = uniqueEmail();
        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "reg-flow-" + UUID.randomUUID())
                .body(body(email))
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body(new com.spotpobre.backend.infrastructure.web.dto.request.AuthenticationRequest(
                        email, "password123"))
                .when()
                .post("/api/v1/auth/authenticate")
                .then()
                .statusCode(200);
    }
}
