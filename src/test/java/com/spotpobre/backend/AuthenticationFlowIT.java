package com.spotpobre.backend;

import com.spotpobre.backend.infrastructure.web.dto.request.AuthenticationRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.RegisterRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationFlowIT extends AbstractFlowIT {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void shouldRegisterAndAuthenticateUserSuccessfully() {
        // 1. Register a new user
        RegisterRequest registerRequest = new RegisterRequest(
                "Integration Test User",
                "integration.test@example.com",
                "password123",
                "US"
        );

        String token = given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "it-reg-" + java.util.UUID.randomUUID())
                .body(registerRequest)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .extract()
                .path("token");

        // 2. Use the token to access a protected endpoint
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/users/me")
                .then()
                .statusCode(200)
                .body("email", equalTo("integration.test@example.com"))
                .body("name", equalTo("Integration Test User"));
    }

    @Test
    void shouldReturn401WhenAuthenticatingWithWrongPassword() {
        // 1. Register a new user
        RegisterRequest registerRequest = new RegisterRequest(
                "Integration Wrong Password User",
                "integration.wrong-password@example.com",
                "password123",
                "US"
        );

        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "it-reg-" + java.util.UUID.randomUUID())
                .body(registerRequest)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(200);

        // 2. Authenticate with the wrong password -> 401, and never leak user existence
        AuthenticationRequest wrongPassword = new AuthenticationRequest(
                "integration.wrong-password@example.com",
                "wrong-password"
        );

        given()
                .contentType(ContentType.JSON)
                .body(wrongPassword)
                .when()
                .post("/api/v1/auth/authenticate")
                .then()
                .statusCode(401);
    }
}
