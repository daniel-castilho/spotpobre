package com.spotpobre.backend;

import com.spotpobre.backend.infrastructure.config.properties.JwtProperties;
import com.spotpobre.backend.infrastructure.web.dto.request.CreatePlaylistRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.RegisterRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.UpdatePlaylistRequest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorHandlingFlowIT extends AbstractFlowIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void protectedEndpoint_withoutToken_shouldReturn401WithStandardBody() {
        given()
                .when()
                .get("/api/v1/me/playlists")
                .then()
                .statusCode(401)
                .body("error", equalTo("Unauthorized"))
                .body("message", equalTo("Authentication required"))
                .body("status", equalTo(401));
    }

    @Test
    void protectedEndpoint_withMalformedToken_shouldReturn401WithStandardBody() {
        given()
                .header("Authorization", "Bearer not-a-valid-jwt")
                .when()
                .get("/api/v1/me/playlists")
                .then()
                .statusCode(401)
                .body("error", equalTo("Unauthorized"))
                .body("message", equalTo("Invalid or expired token"));
    }

    @Test
    void protectedEndpoint_withExpiredToken_shouldReturn401WithStandardBody() {
        String email = "expired.token@example.com";
        registerUser(email);

        String expiredToken = Jwts.builder()
                .subject(email)
                .issuedAt(new Date(System.currentTimeMillis() - 60_000))
                .expiration(new Date(System.currentTimeMillis() - 30_000))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secret().getBytes()))
                .compact();

        given()
                .header("Authorization", "Bearer " + expiredToken)
                .when()
                .get("/api/v1/me/playlists")
                .then()
                .statusCode(401)
                .body("error", equalTo("Unauthorized"))
                .body("message", equalTo("Invalid or expired token"));
    }

    @Test
    void nonOwnerMutation_shouldReturn403WithStandardBody() {
        String ownerToken = registerAndLoginUser("errors.owner@example.com");
        String playlistId = given()
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "it-playlist-" + java.util.UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("Owner Playlist"))
                .when()
                .post("/api/v1/playlists")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String attackerToken = registerAndLoginUser("errors.attacker@example.com");

        given()
                .header("Authorization", "Bearer " + attackerToken)
                .contentType(ContentType.JSON)
                .body(new UpdatePlaylistRequest("Hijacked"))
                .when()
                .patch("/api/v1/playlists/{playlistId}", playlistId)
                .then()
                .statusCode(403)
                .body("error", equalTo("Forbidden"));
    }

    @Test
    void missingResource_shouldReturn404WithStandardBody() {
        String token = registerAndLoginUser("errors.notfound@example.com");

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/playlists/{playlistId}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("error", equalTo("Not Found"))
                .body("message", equalTo("Playlist not found"));
    }

    @Test
    void duplicateEmail_shouldReturn409WithStandardBody() {
        registerUser("errors.duplicate@example.com");

        RegisterRequest duplicate = new RegisterRequest(
                "Duplicate User",
                "errors.duplicate@example.com",
                "password123",
                "US"
        );

        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "it-reg-" + java.util.UUID.randomUUID())
                .body(duplicate)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(409)
                .body("error", equalTo("Conflict"));
    }

    @Test
    void invalidPayload_shouldReturn400WithValidationErrors() {
        RegisterRequest invalid = new RegisterRequest(
                "Bad User",
                "not-an-email",
                "short",
                "US"
        );

        given()
                .contentType(ContentType.JSON)
                .body(invalid)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(400)
                .body("error", equalTo("Validation Error"))
                .body("validationErrors", notNullValue());
    }

    @Test
    void happyPath_stillSucceeds() {
        String token = registerAndLoginUser("errors.happy@example.com");

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/users/me")
                .then()
                .statusCode(200)
                .body("email", equalTo("errors.happy@example.com"));
    }

    private String registerAndLoginUser(String email) {
        return registerUser(email).then().extract().path("token");
    }

    private io.restassured.response.Response registerUser(String email) {
        RegisterRequest registerRequest = new RegisterRequest(
                "Error Handling Test User",
                email,
                "password123",
                "BR"
        );

        return given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "it-reg-" + java.util.UUID.randomUUID())
                .body(registerRequest)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(200)
                .extract()
                .response();
    }
}
