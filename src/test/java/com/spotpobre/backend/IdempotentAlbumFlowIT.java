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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Step 6C end-to-end: album creation behind the durable idempotency protocol (spec §4.3,
 * §5.4–§5.7). The artist-membership authorization is re-checked before the claim on every call,
 * so a 403 never consumes the key and replays cannot bypass the policy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdempotentAlbumFlowIT extends AbstractFlowIT {

    @LocalServerPort
    private int port;

    @Autowired
    private DynamoDbUserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    private String adminToken;
    private String artistToken;
    private String outsiderToken;
    private UUID artistUserId;
    private UUID artistId;

    private final String sharedKey = "it-album-flow-" + UUID.randomUUID();

    private static final String PASSWORD = "password123";

    @BeforeAll
    void setUp() {
        RestAssured.port = port;
        adminToken = seedUserAndLogin("admin.idem.album@example.com", "ADMIN");
        artistToken = seedUserAndLogin("owner.idem.album@example.com", "ARTIST");
        artistUserId = lastSeededUserId;

        // One catalog artist owned by the seeded artist user (admin creates it for them).
        CreateArtistRequest createArtist = new CreateArtistRequest("Idempotency Records", artistUserId);
        artistId = UUID.fromString(given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", "it-album-setup-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body(createArtist)
                .when()
                .post("/api/v1/artists")
                .then()
                .statusCode(201)
                .extract()
                .path("id"));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(new GrantBody(artistUserId))
                .when()
                .post("/api/v1/artists/" + artistId + "/accounts")
                .then()
                .statusCode(201);
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

    private record GrantBody(UUID userId, String permission) {
        private GrantBody(final UUID userId) {
            this(userId, "OWNER");
        }
    }

    private CreateAlbumRequest body() {
        return new CreateAlbumRequest("Debut", artistId, null);
    }

    @Test
    @Order(1)
    void missingKey_isRejectedWith400() {
        given()
                .header("Authorization", "Bearer " + artistToken)
                .contentType(ContentType.JSON)
                .body(body())
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(2)
    void invalidKeyFormat_isRejectedWith400() {
        given()
                .header("Authorization", "Bearer " + artistToken)
                .header("Idempotency-Key", "bad key with spaces!")
                .contentType(ContentType.JSON)
                .body(body())
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(3)
    void nonMemberArtist_failsWithoutConsumingKey_laterGrantAllowsSameKeyToSucceed() {
        // A second ARTIST user with no membership on the catalog artist.
        seedUserAndLogin("outsider.idem.album@example.com", "ARTIST");
        outsiderToken = lastToken;

        given()
                .header("Authorization", "Bearer " + outsiderToken)
                .header("Idempotency-Key", sharedKey)
                .contentType(ContentType.JSON)
                .body(new CreateAlbumRequest("Debut", artistId, null))
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(403);

        // Admin grants the outsider an OWNER membership; the SAME key now succeeds.
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(new GrantBody(lastSeededUserId))
                .when()
                .post("/api/v1/artists/" + artistId + "/accounts")
                .then()
                .statusCode(201);

        given()
                .header("Authorization", "Bearer " + outsiderToken)
                .header("Idempotency-Key", sharedKey)
                .contentType(ContentType.JSON)
                .body(new CreateAlbumRequest("Debut", artistId, null))
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "false")
                .body("id", notNullValue());
    }

    @Test
    @Order(4)
    void sameKeySameRequest_replaysSameAlbumWithReplayHeader() {
        String replayKey = "it-album-replay-" + UUID.randomUUID();
        String firstId = given()
                .header("Authorization", "Bearer " + artistToken)
                .header("Idempotency-Key", replayKey)
                .contentType(ContentType.JSON)
                .body(body())
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "false")
                .extract()
                .path("id");

        given()
                .header("Authorization", "Bearer " + artistToken)
                .header("Idempotency-Key", replayKey)
                .contentType(ContentType.JSON)
                .body(body())
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "true")
                .body("id", equalTo(firstId));
    }

    @Test
    @Order(5)
    void sameKeyDifferentRequest_returns409KeyReuseConflict() {
        // The key was consumed under the OUTSIDER's scope in order 3, so the reuse conflict must
        // also come from that actor (scope includes the authenticated principal).
        CreateAlbumRequest differentBody =
                new CreateAlbumRequest("A Completely Different Record", artistId, null);

        given()
                .header("Authorization", "Bearer " + outsiderToken)
                .header("Idempotency-Key", sharedKey)
                .contentType(ContentType.JSON)
                .body(differentBody)
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(409);
    }

    @Test
    @Order(6)
    void unknownArtist_returns404WithoutConsumingKey() {
        String key = "it-album-unknown-" + UUID.randomUUID();
        UUID missingArtist = UUID.randomUUID();

        given()
                .header("Authorization", "Bearer " + artistToken)
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body(new CreateAlbumRequest("Ghost Album", missingArtist, null))
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(404);

        // Same key against the real artist still works: the key was never consumed.
        given()
                .header("Authorization", "Bearer " + artistToken)
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body(new CreateAlbumRequest("Real Album", artistId, null))
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "false");

        assertNotEquals(missingArtist.toString(), artistId.toString());
    }
}
