package com.spotpobre.backend;

import com.spotpobre.backend.domain.user.port.PasswordHasher;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserProfileDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.repository.DynamoDbUserRepository;
import com.spotpobre.backend.infrastructure.web.dto.request.AuthenticationRequest;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Step 6B end-to-end: admin-only POST /api/v1/artists behind the durable idempotency protocol
 * (spec §4.3, §5.4–§5.7). Covers the done-when matrix for artist creation: missing/invalid key
 * → 400; same key + same request → one artist + replay header; same key + different request →
 * 409 key-reuse conflict; owner validation failures never consume the key.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdempotentArtistFlowIT extends AbstractFlowIT {

    @LocalServerPort
    private int port;

    @Autowired
    private DynamoDbUserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    private String adminToken;
    private UUID ownerUserId;
    private final String sharedKey = "it-artist-flow-" + UUID.randomUUID();

    private static final String PASSWORD = "password123";

    @BeforeAll
    void setUp() {
        RestAssured.port = port;
        adminToken = seedUserAndLogin("admin.idem.artist@example.com", "ADMIN");
        ownerUserId = seedUserId("owner.idem.artist@example.com", "ARTIST");
    }

    private String lastToken;
    private UUID lastSeededUserId;

    private String seedUserAndLogin(String email, String role) {
        seedUserId(email, role);
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

    private UUID seedUserId(String email, String role) {
        lastSeededUserId = UUID.randomUUID();
        UserDocument user = UserDocument.builder()
                .id(lastSeededUserId.toString())
                .profile(UserProfileDocument.builder().name("Test " + role).email(email).country("BR").build())
                .password(passwordHasher.encode(PASSWORD))
                .roles(Set.of(role))
                .build();
        userRepository.save(user);
        return lastSeededUserId;
    }

    private CreateArtistRequest body() {
        return new CreateArtistRequest("The Idempotent Testers", ownerUserId);
    }

    @Test
    @Order(1)
    void missingKey_isRejectedWith400() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(body())
                .when()
                .post("/api/v1/artists")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(2)
    void invalidKeyFormat_isRejectedWith400() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", "bad key with spaces!")
                .contentType(ContentType.JSON)
                .body(body())
                .when()
                .post("/api/v1/artists")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(3)
    void unknownOwner_failsWithoutConsumingKey_laterRetrySucceeds() {
        UUID unknownOwner = UUID.randomUUID();

        given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", sharedKey)
                .contentType(ContentType.JSON)
                .body(new CreateArtistRequest("The Idempotent Testers", unknownOwner))
                .when()
                .post("/api/v1/artists")
                .then()
                .statusCode(404);

        // The key was NOT consumed by the deterministic pre-claim failure: the retry with a
        // valid owner succeeds under the same key.
        given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", sharedKey)
                .contentType(ContentType.JSON)
                .body(body())
                .when()
                .post("/api/v1/artists")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "false")
                .body("id", notNullValue());
    }

    @Test
    @Order(4)
    void sameKeySameRequest_replaysSameArtistWithReplayHeader() {
        String replayKey = "it-artist-replay-" + UUID.randomUUID();
        String firstId = given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", replayKey)
                .contentType(ContentType.JSON)
                .body(body())
                .when()
                .post("/api/v1/artists")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "false")
                .extract()
                .path("id");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", replayKey)
                .contentType(ContentType.JSON)
                .body(body())
                .when()
                .post("/api/v1/artists")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "true")
                .body("id", equalTo(firstId));
    }

    @Test
    @Order(5)
    void sameKeyDifferentRequest_returns409KeyReuseConflict() {
        CreateArtistRequest differentBody =
                new CreateArtistRequest("A Completely Different Band", ownerUserId);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", sharedKey)
                .contentType(ContentType.JSON)
                .body(differentBody)
                .when()
                .post("/api/v1/artists")
                .then()
                .statusCode(409);
    }

    @Test
    @Order(6)
    void differentKeys_createIndependentArtists() {
        String firstId = given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", "it-artist-second-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body(new CreateArtistRequest("Second Idempotent Band", ownerUserId))
                .when()
                .post("/api/v1/artists")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "false")
                .extract()
                .path("id");

        String secondId = given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", "it-artist-third-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body(new CreateArtistRequest("Third Idempotent Band", ownerUserId))
                .when()
                .post("/api/v1/artists")
                .then()
                .statusCode(201)
                .header("Idempotency-Replayed", "false")
                .extract()
                .path("id");

        assertNotEquals(firstId, secondId,
                "different keys must create independent artists");
    }
}
