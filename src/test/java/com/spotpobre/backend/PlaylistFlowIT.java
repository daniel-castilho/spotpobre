package com.spotpobre.backend;

import com.spotpobre.backend.infrastructure.web.dto.request.CreatePlaylistRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.RegisterRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaylistFlowIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private String userToken;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        // Register and authenticate a user to get a token for the tests
        userToken = registerAndLoginUser();
    }

    @Test
    void shouldCreateAndListPlaylistSuccessfully() {
        // 1. Create a new playlist
        CreatePlaylistRequest createPlaylistRequest = new CreatePlaylistRequest("My Awesome Playlist");

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(createPlaylistRequest)
                .when()
                .post("/api/v1/playlists")
                .then()
                .statusCode(201)
                .body("name", equalTo("My Awesome Playlist"))
                .body("id", notNullValue())
                .body("ownerId", notNullValue());

        // 2. List the user's playlists and verify the new one is there
        given()
                .header("Authorization", "Bearer " + userToken)
                .when()
                .get("/api/v1/me/playlists")
                .then()
                .statusCode(200)
                .body("content", hasSize(1))
                .body("content[0].name", equalTo("My Awesome Playlist"));
    }

    private String registerAndLoginUser() {
        RegisterRequest registerRequest = new RegisterRequest(
                "Playlist Test User",
                "playlist.user@example.com",
                "password123",
                "BR"
        );

        return given()
                .contentType(ContentType.JSON)
                .body(registerRequest)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(200)
                .extract()
                .path("token");
    }
}
