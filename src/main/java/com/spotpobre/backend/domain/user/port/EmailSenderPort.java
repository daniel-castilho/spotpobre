package com.spotpobre.backend.domain.user.port;

/**
 * Outbound port for transactional account emails. Operations are semantic (what the user needs
 * to do), never transport/format details: adapters own subjects, bodies, links and templates for
 * their channel. Implementations adapt a concrete provider (AWS SES today, any other tomorrow);
 * swapping the provider must never touch the flows that depend on this port.
 */
public interface EmailSenderPort {

    /** E-mails a single-use password-recovery link embedding {@code rawToken}. */
    void sendPasswordRecoveryEmail(final String to, final String rawToken);
}
