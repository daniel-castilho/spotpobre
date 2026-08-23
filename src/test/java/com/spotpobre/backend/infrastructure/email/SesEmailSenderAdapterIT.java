package com.spotpobre.backend.infrastructure.email;

import com.spotpobre.backend.AbstractIntegrationTest;
import com.spotpobre.backend.domain.user.port.EmailSenderPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Wire-level proof that the SES adapter speaks the LocalStack Community SES v1 emulation: a real
 * send must complete without error against the running edge. Request-building correctness is
 * covered by {@code SesEmailSenderAdapterTest}; orchestration by {@code PasswordRecoveryFlowIT}.
 */
@SpringBootTest
class SesEmailSenderAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private EmailSenderPort emailSenderPort;

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Autowired
    private software.amazon.awssdk.services.ses.SesClient sesClient;

    @Autowired
    private com.spotpobre.backend.infrastructure.config.properties.EmailProperties emailProperties;

    @org.junit.jupiter.api.BeforeEach
    void verifySenderIdentity() {
        // Production requires a verified from-address too (SES rejects unverified senders).
        sesClient.verifyEmailIdentity(software.amazon.awssdk.services.ses.model.VerifyEmailIdentityRequest.builder()
                .emailAddress(emailProperties.fromAddress())
                .build());
    }

    @Test
    void send_againstRealLocalStackSes_completesWithoutError() {
        // Ensure the emulated services this application depends on are reachable first, so a
        // failure here cannot be mistaken for an SES problem.
        ListTablesResponse tables = assertDoesNotThrow(() -> dynamoDbClient.listTables());

        String wireCheckToken = "wire-check-" + System.nanoTime();
        assertDoesNotThrow(() -> emailSenderPort.sendPasswordRecoveryEmail(
                "recipient-" + System.nanoTime() + "@example.com", wireCheckToken));
        assertDoesNotThrow(tables::tableNames);
    }
}
