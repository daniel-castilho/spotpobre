package com.spotpobre.backend;

import com.spotpobre.backend.domain.user.port.EmailSenderPort;
import com.spotpobre.backend.infrastructure.web.dto.request.AuthenticationRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.RegisterRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * End-to-end password recovery journey: request (anonymous, enumeration-safe) → e-mail captured
 * at the {@link EmailSenderPort} boundary → reset with the raw token from the link → old password
 * rejected, new password accepted. The sender is replaced so the test can read the token without
 * depending on LocalStack SES internals; the SES adapter itself is covered separately.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PasswordRecoveryFlowIT extends AbstractFlowIT {

    @LocalServerPort
    private int port;

    @Autowired
    @MockitoBean
    private EmailSenderPort emailSenderPort;

    // Each entry: [to, rawToken] captured at the EmailSenderPort boundary.
    private final List<String[]> sent = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        sent.clear();
        org.mockito.Mockito.doAnswer(inv -> {
            sent.add(new String[]{inv.getArgument(0), inv.getArgument(1)});
            return null;
        }).when(emailSenderPort).sendPasswordRecoveryEmail(any(), anyString());
    }

    @Test
    void recoveryFlow_requestThenReset_replacesPasswordAndBurnsToken() {
        String email = "recovery-" + UUID.randomUUID() + "@example.com";
        register(email, "old-password");

        // 1. Anonymous recover request always acknowledges 202.
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\"}")
                .when().post("/api/v1/auth/password/recover")
                .then().statusCode(202);

        // 2. Unknown e-mails get the same acknowledgement and never send anything.
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\"}")
                .when().post("/api/v1/auth/password/recover")
                .then().statusCode(202);
        assertEquals(1, sent.size(), "unknown addresses must not trigger an e-mail");

        // 3. The adapter received the recipient plus the raw single-use token.
        String[] delivered = sent.get(0);
        assertEquals(email, delivered[0]);
        String rawToken = delivered[1];

        // 4. Reset with the token replaces the password.
        given().contentType(ContentType.JSON)
                .body("{\"token\":\"" + rawToken + "\",\"newPassword\":\"brand-new-pw\"}")
                .when().post("/api/v1/auth/password/reset")
                .then().statusCode(204);

        // 5. Old password no longer authenticates; the new one does.
        assertAuthenticateStatus(email, "old-password", 401);
        assertAuthenticateStatus(email, "brand-new-pw", 200);

        // 6. The same token is single-use: replays answer 404 like any invalid token.
        given().contentType(ContentType.JSON)
                .body("{\"token\":\"" + rawToken + "\",\"newPassword\":\"again-new-pw\"}")
                .when().post("/api/v1/auth/password/reset")
                .then().statusCode(404);
    }

    private void register(String email, String password) {
        given().contentType(ContentType.JSON)
                .header("Idempotency-Key", "recovery-it-" + UUID.randomUUID())
                .body(new RegisterRequest("Recovery User", email, password, "BR"))
                .when().post("/api/v1/auth/register")
                .then().statusCode(200);
    }

    private void assertAuthenticateStatus(String email, String password, int expectedStatus) {
        given().contentType(ContentType.JSON)
                .body(new AuthenticationRequest(email, password))
                .when().post("/api/v1/auth/authenticate")
                .then().statusCode(expectedStatus);
    }
}
