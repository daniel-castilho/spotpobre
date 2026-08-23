package com.spotpobre.backend;

import com.spotpobre.backend.infrastructure.web.dto.request.AuthenticationRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Step 6D end-to-end: playlist creation behind the durable idempotency protocol (spec §4.3,
 * §5.4–§5.7). The authenticated user is both the claim scope and the playlist owner; the
 * per-user playlist limit is enforced at execution time and recorded as a replayable
 * FAILED_FINAL 409.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdempotentPlaylistFlowIT extends AbstractFlowIT {

    @LocalServerPort
    private int port;

    private String userToken;

    @BeforeAll
    void setUp() {
        RestAssured.port = port;
        userToken = registerAndLogin("playlist.idem@example.com");
    }

    private String lastToken;

    private String registerAndLogin(String email) {
        String body = """
                {
                  "name": "Playlist Idempotency User",
                  "email": "%s",
                  "password": "password123",
                  "country": "BR"
                }
                """.formatted(email);

        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "it-reg-" + UUID.randomUUID())
                .body(body)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(200);

        lastToken = given()
                .contentType(ContentType.JSON)
                .body(new AuthenticationRequest(email, "password123"))
                .when()
                .post("/api/v1/auth/authenticate")
                .then()
                .statusCode(200)
                .extract()
                .path("token");
        return lastToken;
    }

    @Test
    @Order(1)
    void missingKey_isRejectedWith400() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("{\"name\": \"No Key Playlist\"}")
                .when()
                .post("/api/v1/playlists")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(2)
    void invalidKeyFormat_isRejectedWith400() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .header("Idempotency-Key", "bad key with spaces!")
                .contentType(ContentType.JSON)
                .body("{\"name\": \"Bad Key Playlist\"}")
                .when()
                .post("/api/v1/playlists")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(3)
    void sameKeySameRequest_replaysSamePlaylistWithReplayHeader() {
        String key = "it-playlist-flow-" + UUID.randomUUID();
        String firstId = given()
                .header("Authorization", "Bearer " + userToken)
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body("{\"name\": \"Road Trip\"}")
                .when()
                .post("/api/v1/playlists")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "false")
                .body("id", notNullValue())
                .extract()
                .path("id");

        given()
                .header("Authorization", "Bearer " + userToken)
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body("{\"name\": \"Road Trip\"}")
                .when()
                .post("/api/v1/playlists")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "true")
                .body("id", equalTo(firstId));
    }

    @Test
    @Order(4)
    void sameKeyDifferentRequest_returns409KeyReuseConflict() {
        String key = "it-playlist-reuse-" + UUID.randomUUID();

        given()
                .header("Authorization", "Bearer " + userToken)
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body("{\"name\": \"First Name\"}")
                .when()
                .post("/api/v1/playlists")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "false");

        given()
                .header("Authorization", "Bearer " + userToken)
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body("{\"name\": \"Second Name\"}")
                .when()
                .post("/api/v1/playlists")
                .then()
                .statusCode(409);
    }

    @Test
    @Order(5)
    void differentUsers_sameKey_areIndependentOperations() {
        String otherToken = registerAndLogin("playlist.idem.other@example.com");
        String sharedKey = "it-playlist-shared-" + UUID.randomUUID();
        var body = "{\"name\": \"Same Name Both Users\"}";

        String firstId = given()
                .header("Authorization", "Bearer " + userToken)
                .header("Idempotency-Key", sharedKey)
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/playlists")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "false")
                .extract()
                .path("id");

        given()
                .header("Authorization", "Bearer " + otherToken)
                .header("Idempotency-Key", sharedKey)
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/playlists")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "false")
                .body("id", org.hamcrest.Matchers.not(equalTo(firstId)));
    }
}
