package com.spotpobre.backend;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.repository.DynamoDbSongMetadataRepository;
import com.spotpobre.backend.infrastructure.web.dto.request.CreateArtistRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.CreatePlaylistRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.RegisterRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.UpdatePlaylistRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaylistFlowIT extends AbstractFlowIT {

    @LocalServerPort
    private int port;

    @Autowired
    private DynamoDbSongMetadataRepository songMetadataRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void shouldCreateAndListPlaylistSuccessfully() {
        String userToken = registerAndLoginUser("playlist.happy@example.com");
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

    @Test
    void shouldRejectPlaylistMutationsByNonOwnerWith403() {
        // 1. User A (owner) creates a playlist
        String ownerToken = registerAndLoginUser("playlist.owner@example.com");
        String playlistId = given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Secure Playlist"))
                .when()
                .post("/api/v1/playlists")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // Seed a song so add/remove-song operations can be exercised end-to-end
        UUID songId = UUID.randomUUID();
        SongDocument songDocument = new SongDocument();
        songDocument.setId(songId.toString());
        songDocument.setTitle("Stolen Song");
        songDocument.setAlbumId(UUID.randomUUID());
        songDocument.setStorageId("storage-key");
        songMetadataRepository.save(songDocument);

        // 2. User B (attacker) registers and logs in
        String attackerToken = registerAndLoginUser("playlist.attacker@example.com");

        // 3. User B tries all four mutations on User A's playlist → expect 403
        //    Update playlist details
        given()
                .header("Authorization", "Bearer " + attackerToken)
                .contentType(ContentType.JSON)
                .body(new UpdatePlaylistRequest("Hijacked Name"))
                .when()
                .patch("/api/v1/playlists/{playlistId}", playlistId)
                .then()
                .statusCode(403)
                .body("error", equalTo("Forbidden"));

        //    Delete playlist
        given()
                .header("Authorization", "Bearer " + attackerToken)
                .when()
                .delete("/api/v1/playlists/{playlistId}", playlistId)
                .then()
                .statusCode(403)
                .body("error", equalTo("Forbidden"));

        //    Add song to playlist (non-owner)
        given()
                .header("Authorization", "Bearer " + attackerToken)
                .when()
                .put("/api/v1/playlists/{playlistId}/songs/{songId}", playlistId, songId)
                .then()
                .statusCode(403)
                .body("error", equalTo("Forbidden"));

        //    Remove song from playlist
        given()
                .header("Authorization", "Bearer " + attackerToken)
                .when()
                .delete("/api/v1/playlists/{playlistId}/songs/{songId}", playlistId, songId)
                .then()
                .statusCode(403)
                .body("error", equalTo("Forbidden"));

        // 4. User A can still perform all four operations successfully
        //    Rename own playlist
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body(new UpdatePlaylistRequest("Renamed by Owner"))
                .when()
                .patch("/api/v1/playlists/{playlistId}", playlistId)
                .then()
                .statusCode(200)
                .body("name", equalTo("Renamed by Owner"));

        //    Add song to own playlist
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .put("/api/v1/playlists/{playlistId}/songs/{songId}", playlistId, songId)
                .then()
                .statusCode(200)
                .body("songs", hasSize(1));

        //    Repeated add of the same song must stay a successful no-op (one membership,
        //    no duplicate) — naturally idempotent desired-state semantics
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .put("/api/v1/playlists/{playlistId}/songs/{songId}", playlistId, songId)
                .then()
                .statusCode(200)
                .body("songs", hasSize(1));

        //    Remove song from own playlist
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .delete("/api/v1/playlists/{playlistId}/songs/{songId}", playlistId, songId)
                .then()
                .statusCode(204);

        //    Repeated remove of the absent song stays successful (no-op, no error)
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .delete("/api/v1/playlists/{playlistId}/songs/{songId}", playlistId, songId)
                .then()
                .statusCode(204);

        //    Delete own playlist
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .delete("/api/v1/playlists/{playlistId}", playlistId)
                .then()
                .statusCode(204);
    }

    private String registerAndLoginUser(String email) {
        RegisterRequest registerRequest = new RegisterRequest(
                "Test User",
                email,
                "password123",
                "BR"
        );

        return given()
                .header("Idempotency-Key", "it-reg-" + java.util.UUID.randomUUID())
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
