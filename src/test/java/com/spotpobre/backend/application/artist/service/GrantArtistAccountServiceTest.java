package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.GrantArtistAccountUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.model.ArtistPermission;
import com.spotpobre.backend.domain.artist.port.ArtistAccountRepository;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrantArtistAccountServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private ArtistAccountRepository artistAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private GrantArtistAccountService grantArtistAccountService;

    private final ArtistId artistId = ArtistId.generate();
    private final UUID targetUserId = UUID.randomUUID();

    private void targetUserExists() {
        when(userRepository.findById(com.spotpobre.backend.domain.user.model.UserId.from(targetUserId.toString())))
                .thenReturn(Optional.of(User.createWithLocalPassword(
                        new UserProfile("Target", "target@example.com", "BR"), "hashed")));
    }

    @Test
    void adminGrantsManagerMembership() {
        when(artistRepository.findById(artistId)).thenReturn(Optional.of(Artist.create("Artist")));
        targetUserExists();
        when(clock.instant()).thenReturn(java.time.Instant.parse("2026-08-23T00:00:00Z"));

        grantArtistAccountService.grant(new GrantArtistAccountUseCase.GrantArtistAccountCommand(
                true, artistId, targetUserId, ArtistPermission.MANAGER));

        ArgumentCaptor<com.spotpobre.backend.domain.artist.model.ArtistAccount> captor =
                ArgumentCaptor.forClass(com.spotpobre.backend.domain.artist.model.ArtistAccount.class);
        verify(artistAccountRepository).save(captor.capture());
        assertEquals(targetUserId, captor.getValue().userId());
        assertEquals(ArtistPermission.MANAGER, captor.getValue().permission());
    }

    @Test
    void nonAdminIsForbidden() {
        assertThrows(ForbiddenException.class, () -> grantArtistAccountService.grant(
                new GrantArtistAccountUseCase.GrantArtistAccountCommand(
                        false, artistId, targetUserId, ArtistPermission.MANAGER)));
        verify(artistAccountRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownArtistFails() {
        when(artistRepository.findById(artistId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> grantArtistAccountService.grant(
                new GrantArtistAccountUseCase.GrantArtistAccountCommand(
                        true, artistId, targetUserId, ArtistPermission.OWNER)));
    }

    @Test
    void unknownTargetUserFailsClosed() {
        when(artistRepository.findById(artistId)).thenReturn(Optional.of(Artist.create("Artist")));
        when(userRepository.findById(com.spotpobre.backend.domain.user.model.UserId.from(targetUserId.toString())))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> grantArtistAccountService.grant(
                new GrantArtistAccountUseCase.GrantArtistAccountCommand(
                        true, artistId, targetUserId, ArtistPermission.MANAGER)));
        verify(artistAccountRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any());
    }
}
