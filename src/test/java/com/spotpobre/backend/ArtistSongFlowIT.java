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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArtistSongFlowIT extends AbstractFlowIT {

    @LocalServerPort
    private int port;

    @Autowired
    private DynamoDbUserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    private String adminToken;
    private String artistToken;
    private UUID artistUserId;

    private static final String PASSWORD = "password123";

    @BeforeAll
    void setUp() {
        RestAssured.port = port;
        // The register endpoint only creates default USER accounts, so ADMIN/ARTIST users are
        // seeded directly in DynamoDB (with a BCrypt-hashed password) and logged in via the
        // authenticate endpoint to obtain tokens carrying the expected roles.
        // Using @BeforeAll (PER_CLASS) ensures users are seeded once, avoiding DynamoDB GSI
        // conflicts and stale cache entries from the @Cacheable UserDetailsServiceImpl.
        seedUserAndLogin("admin.flow@example.com", "ADMIN");
        adminToken = lastToken;
        seedUserAndLogin("artist.flow@example.com", "ARTIST");
        artistToken = lastToken;
        artistUserId = lastSeededUserId;
    }

    private String lastToken;
    private UUID lastSeededUserId;

    private void seedUserAndLogin(String email, String role) {
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
    }

    @Test
    void shouldCreateArtistAndUploadSongSuccessfully() throws Exception {
        // 1. As ADMIN, create a new artist
        CreateArtistRequest createArtistRequest = new CreateArtistRequest("The Integration Testers", artistUserId);
        String artistId = given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", "it-artist-" + java.util.UUID.randomUUID())
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

    @Test
    void shouldDownloadSongContentViaSignedStreamingUrl() throws Exception {
        // 1. As ADMIN, create a new artist
        CreateArtistRequest createArtistRequest = new CreateArtistRequest("Stream Testers", artistUserId);
        String artistId = given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", "it-artist-" + java.util.UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body(createArtistRequest)
                .when()
                .post("/api/v1/artists")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // 2. As ADMIN, create an album for that artist
        CreateAlbumRequest createAlbumRequest = new CreateAlbumRequest(
                "Stream Album", UUID.fromString(artistId), null
        );
        String albumId = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(createAlbumRequest)
                .when()
                .post("/api/v1/albums")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // 3. As ARTIST, initiate a presigned upload for a small audio file
        byte[] audioBytes = "real-mp3-stream-test-data".getBytes();
        String songTitle = "Stream Test Song";
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
                .extract();

        String songId = initiate.path("songId");
        String storageKey = initiate.path("storageKey");
        String uploadUrl = initiate.path("parts[0].url");

        // 4. Upload the file directly to object storage
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest uploadRequest = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(audioBytes))
                .header("Content-Type", "audio/mpeg")
                .build();
        HttpResponse<String> uploadResponse = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, uploadResponse.statusCode());

        // 5. Confirm the upload
        given()
                .header("Authorization", "Bearer " + artistToken)
                .contentType(ContentType.JSON)
                .body(String.format("{\"storageKey\":\"%s\"}", storageKey))
                .when()
                .post("/api/v1/albums/{albumId}/songs/{songId}/confirm", albumId, songId)
                .then()
                .statusCode(200);

        // 6. Retrieve song details — the response must include a valid streamingUrl
        String streamingUrl = given()
                .header("Authorization", "Bearer " + artistToken)
                .when()
                .get("/api/v1/songs/{songId}", songId)
                .then()
                .statusCode(200)
                .body("id", equalTo(songId))
                .body("title", equalTo(songTitle))
                .body("streamingUrl", notNullValue())
                .extract()
                .path("streamingUrl");

        // 7. Perform an HTTP GET on the signed streaming URL
        //    The signed URL must serve the actual audio file that was uploaded.
        URI signedUri = URI.create(streamingUrl);
        HttpResponse<byte[]> downloadResponse = httpClient.send(
                HttpRequest.newBuilder().uri(signedUri).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );

        // 8. Assert status 200, correct Content-Type and body matches uploaded content
        assertEquals(200, downloadResponse.statusCode());
        assertEquals("audio/mpeg", downloadResponse.headers()
                .firstValue("Content-Type").orElse(""));
        assertEquals(audioBytes.length, downloadResponse.body().length);
        assertArrayEquals(audioBytes, downloadResponse.body());
    }
}
