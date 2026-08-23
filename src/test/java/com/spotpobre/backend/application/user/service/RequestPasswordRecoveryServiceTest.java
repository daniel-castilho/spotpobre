package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.RequestPasswordRecoveryUseCase;
import com.spotpobre.backend.domain.user.model.AccountToken;
import com.spotpobre.backend.domain.user.model.AccountTokenPurpose;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.AccountTokenRepository;
import com.spotpobre.backend.domain.user.port.EmailSenderPort;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestPasswordRecoveryServiceTest {

    @Mock
    private Clock clock;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountTokenRepository accountTokenRepository;

    @Mock
    private EmailSenderPort emailSenderPort;

    @InjectMocks
    private RequestPasswordRecoveryService service;

    @Test
    void recovery_knownEmail_issuesTokenAndSendsToAdapter() {
        when(clock.instant()).thenReturn(Instant.parse("2026-08-23T12:00:00Z"));
        String email = "user@example.com";
        User user = User.createWithLocalPassword(new UserProfile("User", email, "BR"), "pw");
        when(userRepository.findByProfileEmail(email)).thenReturn(Optional.of(user));

        service.requestRecovery(new RequestPasswordRecoveryUseCase.RequestPasswordRecoveryCommand(email));

        ArgumentCaptor<AccountToken> tokenCaptor = ArgumentCaptor.forClass(AccountToken.class);
        verify(accountTokenRepository).save(tokenCaptor.capture());
        assertEquals(AccountTokenPurpose.PASSWORD_RESET, tokenCaptor.getValue().purpose());

        // The adapter receives the raw token (which exists only here) scoped to this e-mail,
        // and the stored hash must correspond to that exact raw value.
        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSenderPort).sendPasswordRecoveryEmail(eq(email), rawTokenCaptor.capture());
        assertEquals(AccountToken.hashOf(rawTokenCaptor.getValue()),
                tokenCaptor.getValue().tokenHash());
    }

    @Test
    void recovery_unknownEmail_acknowledgesWithoutSendingOrStoring() {
        when(userRepository.findByProfileEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.requestRecovery(
                new RequestPasswordRecoveryUseCase.RequestPasswordRecoveryCommand("ghost@example.com")));

        verify(accountTokenRepository, never()).save(any());
        verify(emailSenderPort, never()).sendPasswordRecoveryEmail(any(), anyString());
    }

    @Test
    void recovery_senderFailure_isSwallowedToKeepAcknowledgementUniform() {
        when(clock.instant()).thenReturn(Instant.parse("2026-08-23T12:00:00Z"));
        String email = "sender-down@example.com";
        User user = User.createWithLocalPassword(new UserProfile("User", email, "BR"), "pw");
        when(userRepository.findByProfileEmail(email)).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new RuntimeException("ses down"))
                .when(emailSenderPort).sendPasswordRecoveryEmail(any(), anyString());

        assertDoesNotThrow(() -> service.requestRecovery(
                new RequestPasswordRecoveryUseCase.RequestPasswordRecoveryCommand(email)));
        verify(accountTokenRepository).save(any());
    }
}
