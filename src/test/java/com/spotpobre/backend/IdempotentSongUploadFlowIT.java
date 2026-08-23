package com.spotpobre.backend;

import com.spotpobre.backend.domain.user.port.PasswordHasher;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserProfileDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.repository.DynamoDbUserRepository;
import com.spotpobre.backend.infrastructure.web.dto.request.AuthenticationRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.CreateAlbumRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.CreateArtistRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Step 6E end-to-end: song upload initiation behind the durable idempotency protocol (120 s
 * lease). Replays return the SAME song id with a freshly presigned URL, so a client that lost
 * its connection resumes against the exact object bound to its reserved song.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdempotentSongUploadFlowIT extends AbstractFlowIT {

    @LocalServerPort
    private int port;

    @Autowired
    private DynamoDbUserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    private String artistToken;
    private UUID artistUserId;
    private UUID albumId;

    private static final String PASSWORD = "password123";

    @BeforeAll
    void setUp() {
        RestAssured.port = port;
        String adminToken = seedUserAndLogin("admin.idem.song@example.com", "ADMIN");
        artistToken = seedUserAndLogin("owner.idem.song@example.com", "ARTIST");
        artistUserId = lastSeededUserId;

        String artistId = given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", "it-song-setup-a-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body(new CreateArtistRequest("Song Idempotency Band", artistUserId))
                .when()
                .post("/api/v1/artists")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        albumId = UUID.fromString(given()
                .header("Authorization", "Bearer " + artistToken)
                .header("Idempotency-Key", "it-song-setup-al-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body(new CreateAlbumRequest("Vinyl Dreams", UUID.fromString(artistId), null))
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(201)
                .extract()
                .path("id"));
    }

    private String lastToken;
    private UUID lastSeededUserId;

    private String seedUserAndLogin(String email, String role) {
        lastSeededUserId = UUID.randomUUID();
        UserDocument user = UserDocument.builder()
                .id(lastSeededUserId.toString())
                .profile(UserProfileDocument.builder().name("Test " + role).email(email).country("BR").build())
                .password(passwordHasher.encode(PASSWORD))
                .roles(Set.of(role))
                .build();
        userRepository.save(user);

        AuthenticationRequest authRequest = new AuthenticationRequest(email, PASSWORD);
        lastToken = given()
                .contentType(ContentType.JSON)
                .body(authRequest)
                .when()
                .post("/api/v1/auth/authenticate")
                .then()
                .statusCode(200)
                .extract()
                .path("token");
        return lastToken;
    }

    private io.restassured.response.Response initiate(final String token, final String key,
                                                      final String title) {
        var request = given()
                .header("Authorization", "Bearer " + token);
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return request
                .contentType(ContentType.JSON)
                .body("""
                        {"title": "%s", "contentType": "audio/mpeg", "contentLengthBytes": 1048576}
                        """.formatted(title))
                .when()
                .post("/api/v1/albums/{albumId}/songs", albumId);
    }

    @Test
    @Order(1)
    void missingKey_isRejectedWith400() {
        initiate(artistToken, null, "No Key Track")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(2)
    void invalidKeyFormat_isRejectedWith400() {
        initiate(artistToken, "bad key with spaces!", "Bad Key Track")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(3)
    void sameKeySameRequest_replaysSameSongWithFreshPresignedUrl() {
        String key = "it-upload-flow-" + UUID.randomUUID();

        var first = initiate(artistToken, key, "Midnight Drive")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "false")
                .body("songId", notNullValue())
                .body("parts[0].url", notNullValue())
                .extract();

        var second = initiate(artistToken, key, "Midnight Drive")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "true")
                .body("songId", notNullValue())
                .body("parts[0].url", notNullValue())
                .extract();

        String firstSongId = first.path("songId");
        String secondSongId = second.path("songId");
        org.junit.jupiter.api.Assertions.assertEquals(
                firstSongId, secondSongId,
                "replay must return the reserved song id");
        String firstStorageKey = first.path("storageKey");
        String secondStorageKey = second.path("storageKey");
        org.junit.jupiter.api.Assertions.assertEquals(
                firstStorageKey, secondStorageKey,
                "replay must target the storage key bound to the reserved song");
        // Note: the replayed URL is genuinely re-presigned, but SigV4 URLs are deterministic
        // within the same second, so byte-inequality cannot be asserted reliably here.
        String firstExpiresAt = first.path("expiresAt");
        String secondExpiresAt = second.path("expiresAt");
        org.junit.jupiter.api.Assertions.assertNotEquals(
                firstExpiresAt, secondExpiresAt,
                "replay must carry a fresh expiry");
    }

    @Test
    @Order(4)
    void sameKeyDifferentRequest_returns409KeyReuseConflict() {
        String key = "it-upload-reuse-" + UUID.randomUUID();

        initiate(artistToken, key, "First Take")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "false");

        initiate(artistToken, key, "Second Take")
                .then()
                .statusCode(409);
    }

    @Test
    @Order(5)
    void unknownAlbum_returns404WithoutConsumingKey() {
        String key = "it-upload-unknown-" + UUID.randomUUID();
        UUID missingAlbum = UUID.randomUUID();

        given()
                .header("Authorization", "Bearer " + artistToken)
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body("{\"title\": \"Ghost Track\", \"contentType\": \"audio/mpeg\", " +
                        "\"contentLengthBytes\": 1048576}")
                .when()
                .post("/api/v1/albums/{albumId}/songs", missingAlbum)
                .then()
                .statusCode(404);

        // Same key against the real album still works: the key was never consumed.
        initiate(artistToken, key, "Real Track")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "false")
                .body("songId", notNullValue());
    }
}
