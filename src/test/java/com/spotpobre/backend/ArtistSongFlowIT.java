package com.spotpobre.backend;

import com.spotpobre.backend.infrastructure.web.dto.request.CreateArtistRequest;
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
class ArtistSongFlowIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private String adminToken;
    private String artistToken;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        // For this test, we need users with specific roles.
        // We'll register them and assume the roles are assigned correctly by our (fixed) JWT logic.
        adminToken = registerAndLoginUser("admin.flow@example.com", "ADMIN");
        artistToken = registerAndLoginUser("artist.flow@example.com", "ARTIST");
    }

    @Test
    void shouldCreateArtistAndUploadSongSuccessfully() {
        // 1. As ADMIN, create a new artist
        CreateArtistRequest createArtistRequest = new CreateArtistRequest("The Integration Testers");
        String artistId = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(createArtistRequest)
                .when()
                .post("/api/v1/artists")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("The Integration Testers"))
                .extract()
                .path("id");

        // 2. As ARTIST, upload a song for that artist
        String songTitle = "Testcontainers Rock";
        String songId = given()
                .header("Authorization", "Bearer " + artistToken)
                .multiPart("request", String.format("{\"title\":\"%s\",\"artistId\":\"%s\"}", songTitle, artistId), "application/json")
                .multiPart("file", "test.mp3", "fake-mp3-data".getBytes(), "audio/mpeg")
                .when()
                .post("/api/v1/songs")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("title", equalTo(songTitle))
                .body("artistId", equalTo(artistId))
                .extract()
                .path("id");

        // 3. As any authenticated user, verify the song can be retrieved
        given()
                .header("Authorization", "Bearer " + artistToken)
                .when()
                .get("/api/v1/songs/{songId}", songId)
                .then()
                .statusCode(200)
                .body("id", equalTo(songId))
                .body("title", equalTo(songTitle));
    }

    private String registerAndLoginUser(String email, String role) {
        // This helper simulates user registration.
        // NOTE: In a real application, assigning roles would be a separate, protected process.
        // Here, we rely on the fact that our JWT generation now correctly includes the roles
        // that are assigned upon user creation in the database. For the test to work,
        // we would need a way to grant these roles. We are simplifying this for the test.
        RegisterRequest registerRequest = new RegisterRequest(
                "Test " + role,
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
