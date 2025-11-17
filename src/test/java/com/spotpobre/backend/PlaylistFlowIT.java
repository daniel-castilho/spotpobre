package com.spotpobre.backend;

import com.spotpobre.backend.infrastructure.web.dto.request.CreateArtistRequest;
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
        userToken = registerAndLoginUser("playlist.user@example.com");
    }

    @Test
    void shouldCreateAndListPlaylistSuccessfully() {
        CreatePlaylistRequest createPlaylistRequest = new CreatePlaylistRequest("My Awesome Playlist");

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(createPlaylistRequest)
                .when()
                .post("/api/v1/playlists")
                .then()
                .statusCode(201)
                .body("name", equalTo("My Awesome Playlist"));

        given()
                .header("Authorization", "Bearer " + userToken)
                .when()
                .get("/api/v1/me/playlists")
                .then()
                .statusCode(200)
                .body("content", hasSize(1))
                .body("content[0].name", equalTo("My Awesome Playlist"));
    }

    // Note: A more comprehensive test for adding a song would require setting up an artist and a song first.
    // This would involve either a separate admin endpoint to grant roles or direct database manipulation.
    // For now, this test is commented out to keep the setup simple.
    /*
    @Test
    void shouldAddSongToPlaylist() {
        // This test requires a more complex setup and is left as an exercise.
    }
    */

    private String registerAndLoginUser(String email) {
        RegisterRequest registerRequest = new RegisterRequest(
                "Test User",
                email,
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
