package com.spotpobre.backend;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.repository.DynamoDbSongMetadataRepository;
import com.spotpobre.backend.infrastructure.web.dto.request.RegisterRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.UUID;

import static io.restassured.RestAssured.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeFlowIT extends AbstractFlowIT {

    @LocalServerPort
    private int port;

    @Autowired
    private DynamoDbSongMetadataRepository songMetadataRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void putAndDeleteLikeAreIdempotentDesiredStateOperations() {
        String userToken = registerAndLoginUser("like.flow@example.com");

        UUID songId = seedSong("Likeable Song");

        // First PUT creates the like
        given()
                .header("Authorization", "Bearer " + userToken)
                .when()
                .put("/api/v1/users/me/likes/song/{songId}", songId)
                .then()
                .statusCode(204);

        // Repeated PUT stays successful (one like, original likedAt preserved)
        given()
                .header("Authorization", "Bearer " + userToken)
                .when()
                .put("/api/v1/users/me/likes/song/{songId}", songId)
                .then()
                .statusCode(204);

        // DELETE removes the like
        given()
                .header("Authorization", "Bearer " + userToken)
                .when()
                .delete("/api/v1/users/me/likes/song/{songId}", songId)
                .then()
                .statusCode(204);

        // Repeated DELETE stays successful (no like, no error)
        given()
                .header("Authorization", "Bearer " + userToken)
                .when()
                .delete("/api/v1/users/me/likes/song/{songId}", songId)
                .then()
                .statusCode(204);
    }

    @Test
    void likeMutationWithInvalidEntityTypeFails400() {
        String userToken = registerAndLoginUser("like.invalid-type@example.com");

        given()
                .header("Authorization", "Bearer " + userToken)
                .when()
                .put("/api/v1/users/me/likes/label/{entityId}", UUID.randomUUID())
                .then()
                .statusCode(400);
    }

    @Test
    void likeMutationForMissingEntityReturns404() {
        String userToken = registerAndLoginUser("like.missing-entity@example.com");
        UUID missingSongId = UUID.randomUUID();

        given()
                .header("Authorization", "Bearer " + userToken)
                .when()
                .put("/api/v1/users/me/likes/song/{songId}", missingSongId)
                .then()
                .statusCode(404);

        given()
                .header("Authorization", "Bearer " + userToken)
                .when()
                .delete("/api/v1/users/me/likes/song/{songId}", missingSongId)
                .then()
                .statusCode(404);
    }

    @Test
    void likeMutationWithoutAuthenticationIsRejected() {
        UUID songId = UUID.randomUUID();

        given()
                .when()
                .put("/api/v1/users/me/likes/song/{songId}", songId)
                .then()
                .statusCode(401);

        given()
                .when()
                .delete("/api/v1/users/me/likes/song/{songId}", songId)
                .then()
                .statusCode(401);
    }

    private UUID seedSong(String title) {
        UUID songId = UUID.randomUUID();
        SongDocument songDocument = new SongDocument();
        songDocument.setId(songId.toString());
        songDocument.setTitle(title);
        songDocument.setAlbumId(UUID.randomUUID());
        songDocument.setStorageId("storage-key-" + songId);
        songMetadataRepository.save(songDocument);
        return songId;
    }

    private String registerAndLoginUser(String email) {
        RegisterRequest registerRequest = new RegisterRequest(
                "Like User",
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
