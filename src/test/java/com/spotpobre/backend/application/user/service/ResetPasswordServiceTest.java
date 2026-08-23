package com.spotpobre.backend.application.user.service;

import com.spotpobre.backend.application.user.port.in.ResetPasswordUseCase;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.user.model.AccountToken;
import com.spotpobre.backend.domain.user.model.AccountTokenPurpose;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.AccountTokenRepository;
import com.spotpobre.backend.domain.user.port.PasswordHasher;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResetPasswordServiceTest {

    @Mock
    private Clock clock;

    @Mock
    private AccountTokenRepository accountTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private ResetPasswordService resetPasswordService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(clock.instant()).thenReturn(Instant.parse("2026-08-23T12:00:00Z"));
    }

    @Test
    void reset_validToken_encodesNewPasswordSavesAndBurnsToken() {
        String raw = "raw-token-value";
        String hash = AccountToken.hashOf(raw);
        var user = User.createWithLocalPassword(new UserProfile("U", "u@example.com", "BR"), "old");
        AccountToken token = AccountToken.issue(user.getId(), AccountTokenPurpose.PASSWORD_RESET,
                raw, AccountToken.DEFAULT_TTL, clock.instant().minusSeconds(60));

        when(accountTokenRepository.findActiveByHash(hash, AccountTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordHasher.encode("brand-new-pw")).thenReturn("encoded-new");

        resetPasswordService.resetPassword(
                new ResetPasswordUseCase.ResetPasswordCommand(raw, "brand-new-pw"));

        assertEquals("encoded-new", user.getPassword());
        verify(userRepository).save(user);
        verify(accountTokenRepository).markUsed(hash);
    }

    @Test
    void reset_unknownOrRedeemedToken_answersNotFound() {
        when(accountTokenRepository.findActiveByHash(anyString(), any())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> resetPasswordService.resetPassword(
                new ResetPasswordUseCase.ResetPasswordCommand("whatever", "new-pw-123")));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void reset_expiredToken_answersNotFoundEvenIfRowStillExists() {
        String raw = "expired-token";
        var user = User.createWithLocalPassword(new UserProfile("U", "u2@example.com", "BR"), "old");
        AccountToken expired = AccountToken.issue(user.getId(), AccountTokenPurpose.PASSWORD_RESET,
                raw, AccountToken.DEFAULT_TTL, clock.instant().minusSeconds(3600));
        when(accountTokenRepository.findActiveByHash(expired.tokenHash(), AccountTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(expired));

        assertThrows(NotFoundException.class, () -> resetPasswordService.resetPassword(
                new ResetPasswordUseCase.ResetPasswordCommand(raw, "new-pw-123")));
        verify(userRepository, never()).save(user);
        verify(accountTokenRepository, never()).markUsed(anyString());
    }
}
