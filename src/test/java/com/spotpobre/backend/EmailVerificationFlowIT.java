package com.spotpobre.backend;

import com.spotpobre.backend.domain.user.port.EmailSenderPort;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * End-to-end e-mail verification journey (binding decisions v0.12.0): auto-send on FIRST
 * successful registration only (idempotent replays never resend), authenticated resend with
 * per-user cooldown (429), anonymous POST confirmation burning the single-use token, and the
 * {@code emailVerified} flag surfacing on the profile without ever blocking login.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmailVerificationFlowIT extends AbstractFlowIT {

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
        doAnswer(inv -> {
            sent.add(new String[]{inv.getArgument(0), inv.getArgument(1)});
            return null;
        }).when(emailSenderPort).sendEmailVerificationEmail(anyString(), anyString());
        // Recovery e-mails share the mocked port but are out of scope here.
        doAnswer(inv -> null).when(emailSenderPort).sendPasswordRecoveryEmail(any(), anyString());
    }

    private String registerReturningToken(String idempotencyKey) {
        String email = "verify-" + UUID.randomUUID() + "@example.com";
        given().contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(new RegisterRequest("Verify User", email, "password123", "BR"))
                .when().post("/api/v1/auth/register")
                .then().statusCode(200);
        return email;
    }

    private String authenticate(String email) {
        return given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"password123\"}")
                .when().post("/api/v1/auth/authenticate")
                .then().statusCode(200)
                .extract().path("token");
    }

    @Test
    void verificationFlow_autoSendOnce_resendThrottled_confirmBurnsTokenAndFlagsProfile() {
        // 1. First successful registration sends exactly one verification e-mail.
        String idempotencyKey = "verify-it-" + UUID.randomUUID();
        String email = registerReturningToken(idempotencyKey);
        assertEquals(1, sent.size(), "registration must trigger one verification e-mail");
        assertEquals(email, sent.get(0)[0]);

        // 2. Idempotency REPLAY of the same registration must not resend.
        given().contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(new RegisterRequest("Verify User", email, "password123", "BR"))
                .when().post("/api/v1/auth/register")
                .then().statusCode(200);
        assertEquals(1, sent.size(), "replays must not resend the verification e-mail");

        // 3. Profile exposes the unverified state before confirmation.
        String jwt = authenticate(email);
        given().header("Authorization", "Bearer " + jwt)
                .when().get("/api/v1/users/me")
                .then().statusCode(200)
                .body("emailVerified", org.hamcrest.Matchers.equalTo(false));

        // 4. Authenticated resend delivers a second, fresh token.
        given().header("Authorization", "Bearer " + jwt)
                .when().post("/api/v1/auth/email/verification/resend")
                .then().statusCode(202);
        assertEquals(2, sent.size());

        // 5. Per-user cooldown: an immediate second resend answers 429.
        given().header("Authorization", "Bearer " + jwt)
                .when().post("/api/v1/auth/email/verification/resend")
                .then().statusCode(429);

        // 6. Confirm with the LATEST token: success burns it and flags the account.
        String rawToken = sent.get(1)[1];
        given().contentType(ContentType.JSON)
                .body("{\"token\":\"" + rawToken + "\"}")
                .when().post("/api/v1/auth/email/verification/confirm")
                .then().statusCode(204);

        given().header("Authorization", "Bearer " + jwt)
                .when().get("/api/v1/users/me")
                .then().statusCode(200)
                .body("emailVerified", org.hamcrest.Matchers.equalTo(true));

        // 7. Replay of the burnt token answers 404 like any invalid one.
        given().contentType(ContentType.JSON)
                .body("{\"token\":\"" + rawToken + "\"}")
                .when().post("/api/v1/auth/email/verification/confirm")
                .then().statusCode(404);

        // 8. Malformed body (blank token) answers 400.
        given().contentType(ContentType.JSON)
                .body("{\"token\":\"\"}")
                .when().post("/api/v1/auth/email/verification/confirm")
                .then().statusCode(400);
    }
}
