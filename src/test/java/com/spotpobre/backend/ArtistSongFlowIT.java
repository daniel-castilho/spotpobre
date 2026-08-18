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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArtistSongFlowIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private DynamoDbUserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    private String adminToken;
    private String artistToken;

    private static final String PASSWORD = "password123";

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        // The register endpoint only creates default USER accounts, so ADMIN/ARTIST users are
        // seeded directly in DynamoDB (with a BCrypt-hashed password) and logged in via the
        // authenticate endpoint to obtain tokens carrying the expected roles.
        seedUserAndLogin("admin.flow@example.com", "ADMIN");
        adminToken = lastToken;
        seedUserAndLogin("artist.flow@example.com", "ARTIST");
        artistToken = lastToken;
    }

    private String lastToken;

    private void seedUserAndLogin(String email, String role) {
        UserDocument user = UserDocument.builder()
                .id(UUID.randomUUID().toString())
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
    }

    @Test
    void shouldCreateArtistAndUploadSongSuccessfully() throws Exception {
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

        // 2. As ADMIN, create an album for that artist
        CreateAlbumRequest createAlbumRequest = new CreateAlbumRequest(
                "Integration Album",
                UUID.fromString(artistId),
                null,
                null
        );
        String albumId = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(createAlbumRequest)
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("Integration Album"))
                .extract()
                .path("id");

        // 3. As ARTIST, request a presigned upload URL (no file bytes through the API)
        String songTitle = "Testcontainers Rock";
        byte[] audioBytes = "fake-mp3-data".getBytes();
        String initiateBody = String.format(
                "{\"title\":\"%s\",\"contentType\":\"audio/mpeg\",\"contentLengthBytes\":%d}",
                songTitle, audioBytes.length
        );
        var initiate = given()
                .header("Authorization", "Bearer " + artistToken)
                .contentType(ContentType.JSON)
                .body(initiateBody)
                .when()
                .post("/api/v1/albums/{albumId}/songs", albumId)
                .then()
                .statusCode(201)
                .body("songId", notNullValue())
                .body("title", equalTo(songTitle))
                .body("storageKey", notNullValue())
                .body("parts[0].url", notNullValue())
                .extract();

        String songId = initiate.path("songId");
        String storageKey = initiate.path("storageKey");
        String uploadUrl = initiate.path("parts[0].url");

        // 4. Client uploads directly to object storage
        // Note: RestAssured double-encodes query parameters in presigned S3 URLs (e.g. %3B -> %253B),
        // which causes LocalStack 3.x to return 500 InternalError when validating the signature.
        // Using the JDK HttpClient directly preserves the original URL encoding.
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest uploadRequest = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(audioBytes))
                .header("Content-Type", "audio/mpeg")
                .build();
        HttpResponse<String> uploadResponse = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
        org.junit.jupiter.api.Assertions.assertEquals(200, uploadResponse.statusCode());

        // 5. Confirm the upload
        given()
                .header("Authorization", "Bearer " + artistToken)
                .contentType(ContentType.JSON)
                .body(String.format("{\"storageKey\":\"%s\"}", storageKey))
                .when()
                .post("/api/v1/albums/{albumId}/songs/{songId}/confirm", albumId, songId)
                .then()
                .statusCode(200)
                .body("id", equalTo(songId))
                .body("title", equalTo(songTitle));

        // 6. As any authenticated user, verify the song can be retrieved
        given()
                .header("Authorization", "Bearer " + artistToken)
                .when()
                .get("/api/v1/songs/{songId}", songId)
                .then()
                .statusCode(200)
                .body("id", equalTo(songId))
                .body("title", equalTo(songTitle));
    }
}
