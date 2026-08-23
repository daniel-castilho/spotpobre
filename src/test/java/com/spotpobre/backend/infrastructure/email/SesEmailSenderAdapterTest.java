package com.spotpobre.backend.infrastructure.email;

import com.spotpobre.backend.domain.user.port.EmailSenderPort;
import com.spotpobre.backend.infrastructure.config.properties.AppProperties;
import com.spotpobre.backend.infrastructure.config.properties.EmailProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SesEmailSenderAdapterTest {

    private final SesClient sesClient = mock(SesClient.class);
    private final EmailProperties emailProperties =
            new EmailProperties("no-reply@spotpobre.local", "http://localhost:4566", java.time.Duration.ofHours(24));
    private final AppProperties appProperties = new AppProperties("https://app.example.com");
    private final SesEmailSenderAdapter adapter =
            new SesEmailSenderAdapter(sesClient, emailProperties, appProperties);

    @Test
    void sendPasswordRecoveryEmail_composesLinkAndSendsViaSes() {
        {
            org.mockito.Mockito.when(sesClient.sendEmail(any(SendEmailRequest.class)))
                    .thenReturn(software.amazon.awssdk.services.ses.model.SendEmailResponse.builder()
                            .messageId("mid")
                            .build());
        }

        adapter.sendPasswordRecoveryEmail("user@example.com", "raw-token-value");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        SendEmailRequest request = captor.getValue();

        assertEquals("no-reply@spotpobre.local", request.source());
        assertEquals("user@example.com", request.destination().toAddresses().get(0));

        String text = request.message().body().text().data();
        String html = request.message().body().html().data();
        assertTrue(text.contains("token=raw-token-value"), "text body must embed the reset link");
        assertTrue(text.contains("https://app.example.com/reset-password?token=raw-token-value"));
        assertTrue(html.contains("token=raw-token-value"), "html body must embed the reset link");
        assertEquals("Subject line".isEmpty() ? "wrong" : request.message().subject().data(),
                "Spotpobre — password recovery");
    }

    @Test
    void sendPasswordRecoveryEmail_secondCall_usesFreshTokenInNewLink() {
        org.mockito.Mockito.when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(software.amazon.awssdk.services.ses.model.SendEmailResponse.builder()
                        .messageId("mid").build());

        adapter.sendPasswordRecoveryEmail("a@example.com", "token-A");
        adapter.sendPasswordRecoveryEmail("b@example.com", "token-B");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient, org.mockito.Mockito.times(2)).sendEmail(captor.capture());
        var requests = captor.getAllValues();
        assertTrue(requests.get(0).message().body().text().data().contains("token=token-A"));
        assertTrue(requests.get(1).message().body().text().data().contains("token=token-B"));
        assertEquals("a@example.com", requests.get(0).destination().toAddresses().get(0));
        assertEquals("b@example.com", requests.get(1).destination().toAddresses().get(0));
    }
}
