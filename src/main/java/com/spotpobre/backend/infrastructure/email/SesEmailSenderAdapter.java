package com.spotpobre.backend.infrastructure.email;

import com.spotpobre.backend.domain.user.port.EmailSenderPort;
import com.spotpobre.backend.infrastructure.config.properties.AppProperties;
import com.spotpobre.backend.infrastructure.config.properties.EmailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.time.Duration;

/**
 * AWS SES adapter for {@link EmailSenderPort} (SES v1 API — the surface LocalStack Community
 * emulates). Owns subjects, bodies and link composition; the application only states what
 * happened. Swapping to another vendor means writing a new adapter, nothing else changes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SesEmailSenderAdapter implements EmailSenderPort {

    static final Duration RECOVERY_TTL = Duration.ofMinutes(30);

    private final SesClient sesClient;
    private final EmailProperties emailProperties;
    private final AppProperties appProperties;

    @Override
    public void sendPasswordRecoveryEmail(final String to, final String rawToken) {
        final String minutes = Long.toString(RECOVERY_TTL.toMinutes());
        final String resetLink = appProperties.baseUrl() + "/reset-password?token=" + rawToken;
        send(to, "Spotpobre — password recovery",
                "Use the link below to reset your password. It expires in "
                        + minutes + " minutes.\n" + resetLink,
                "<p>Use the link below to reset your password. It expires in "
                        + minutes + " minutes.</p>"
                        + "<p><a href=\"" + resetLink + "\">Reset my password</a></p>");
    }

    @Override
    public void sendEmailVerificationEmail(final String to, final String rawToken) {
        final long hours = emailProperties.verificationTtl().toHours();
        // Decision (v0.12.0): no links to routes that do not exist. The e-mail carries the
        // copyable token plus the canonical POST contract; a frontend may build its own link
        // and call the API on the user's behalf.
        final String contract =
                "Confirm your e-mail address by calling:\n"
                + "POST " + appProperties.baseUrl() + "/api/v1/auth/email/verification/confirm\n"
                + "with body: {\"token\":\"" + rawToken + "\"}\n\n"
                + "Copyable token: " + rawToken;
        final String htmlContract =
                "<p>Confirm your e-mail address by calling</p>"
                + "<pre>POST " + appProperties.baseUrl()
                + "/api/v1/auth/email/verification/confirm\n"
                + "{\"token\":\"" + rawToken + "\"}</pre>"
                + "<p>Token: <code>" + rawToken + "</code> (expires in "
                + hours + " hours)</p>";
        send(to, "Spotpobre — verify your e-mail",
                "Your verification token expires in " + hours + " hours.\n\n" + contract,
                htmlContract);
    }

    private void send(final String to, final String subject, final String textBody, final String htmlBody) {
        final Message content = Message.builder()
                .subject(Content.builder().data(subject).charset("UTF-8").build())
                .body(Body.builder()
                        .text(Content.builder().data(textBody).charset("UTF-8").build())
                        .html(htmlOrNull(htmlBody))
                        .build())
                .build();

        final SendEmailRequest request = SendEmailRequest.builder()
                .source(emailProperties.fromAddress())
                .destination(Destination.builder().toAddresses(to).build())
                .message(content)
                .build();

        final String messageId = sesClient.sendEmail(request).messageId();
        log.info("Email sent via SES to {} (messageId={})",
                com.spotpobre.backend.infrastructure.common.Redaction.maskEmail(to), messageId);
    }

    private static Content htmlOrNull(final String htmlBody) {
        if (htmlBody == null || htmlBody.isBlank()) {
            return null;
        }
        return Content.builder().data(htmlBody).charset("UTF-8").build();
    }
}
