package com.spotpobre.backend;

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
class AuthenticationFlowIT extends AbstractIntegrationTest {

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
}
