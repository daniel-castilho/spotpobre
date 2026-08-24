package com.spotpobre.backend;

import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.UserProfileDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.repository.DynamoDbUserRepository;
import com.spotpobre.backend.domain.user.port.PasswordHasher;
import com.spotpobre.backend.infrastructure.web.dto.request.AuthenticationRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Proves the User ↔ Artist ownership policy end-to-end against LocalStack (spec §6.1–§6.4):
 * an explicit MANAGER membership is persisted and honoured, an unrelated ARTIST is rejected
 * with 403, ADMIN override succeeds, duplicate grants stay idempotent, and granting to a
 * nonexistent user fails closed with 404.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArtistAccountAccessFlowIT extends AbstractFlowIT {

    @LocalServerPort
    private int port;

    @Autowired
    private DynamoDbUserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    private static final String PASSWORD = "password123";

    private String adminToken;
    private String managerToken;
    private String outsiderToken;
    private UUID ownerUserId;
    private UUID managerUserId;

    @BeforeAll
    void setUp() {
        RestAssured.port = port;
        adminToken = seedAndLogin("acc-admin@example.com", "ADMIN");
        ownerUserId = seedUserOnly("acc-owner@example.com", "ARTIST");
        seedAndLogin("acc-owner@example.com", "ARTIST"); // login only; token unused
        managerUserId = seedUserOnly("acc-manager@example.com", "ARTIST");
        managerToken = login("acc-manager@example.com");
        seedUserOnly("acc-outsider@example.com", "ARTIST");
        outsiderToken = login("acc-outsider@example.com");
    }

    private UUID lastSeededUserId;

    private String seedAndLogin(String email, String role) {
        seedUserOnly(email, role);
        return login(email);
    }

    private UUID seedUserOnly(String email, String role) {
        lastSeededUserId = UUID.randomUUID();
        UserDocument user = UserDocument.builder()
                .id(lastSeededUserId.toString())
                .profile(UserProfileDocument.builder().name("Acc " + role).email(email).country("BR").build())
                .password(passwordHasher.encode(PASSWORD))
                .roles(Set.of(role))
                .build();
        userRepository.save(user);
        return lastSeededUserId;
    }

    private String login(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body(new AuthenticationRequest(email, PASSWORD))
                .when()
                .post("/api/v1/auth/authenticate")
                .then()
                .statusCode(200)
                .extract()
                .path("token");
    }

    @Test
    void managerMembershipIsEnforcedAndUnrelatedArtistIsForbidden() {
        // Admin creates an artist owned by the seeded OWNER account.
        String artistId = given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", "it-acc-artist-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Access Flow Artists\",\"ownerUserId\":\"%s\"}".formatted(ownerUserId))
                .when()
                .post("/api/v1/artists")
                .then()
                .statusCode(201)
                .body("name", equalTo("Access Flow Artists"))
                .extract()
                .path("id");

        // Admin grants an explicit MANAGER membership.
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{\"userId\":\"%s\",\"permission\":\"MANAGER\"}".formatted(managerUserId))
                .when()
                .post("/api/v1/artists/{artistId}/accounts", artistId)
                .then()
                .statusCode(201)
                .body("permission", equalTo("MANAGER"))
                .body("userId", notNullValue());

        // Duplicate identical grant stays defined (idempotent overwrite, no error).
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{\"userId\":\"%s\",\"permission\":\"MANAGER\"}".formatted(managerUserId))
                .when()
                .post("/api/v1/artists/{artistId}/accounts", artistId)
                .then()
                .statusCode(201);

        // Granting to a nonexistent user fails closed with 404.
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{\"userId\":\"%s\",\"permission\":\"OWNER\"}".formatted(UUID.randomUUID()))
                .when()
                .post("/api/v1/artists/{artistId}/accounts", artistId)
                .then()
                .statusCode(404);

        // MANAGER may manage the artist (album creation).
        given()
                .header("Authorization", "Bearer " + managerToken)
                .header("Idempotency-Key", "it-acc-album-manager-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Manager Album\",\"artistId\":\"%s\"}".formatted(artistId))
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(201)
                .body("name", equalTo("Manager Album"));

        // Unrelated ROLE_ARTIST without any membership receives 403 (fail closed).
        given()
                .header("Authorization", "Bearer " + outsiderToken)
                .header("Idempotency-Key", "it-acc-album-out-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Outsider Album\",\"artistId\":\"%s\"}".formatted(artistId))
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(403);

        // Explicit ADMIN override still works.
        given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", "it-acc-album-admin-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Admin Override Album\",\"artistId\":\"%s\"}".formatted(artistId))
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(201);
    }
}
