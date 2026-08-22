package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.RevokeArtistAccountUseCase;
import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.model.ArtistPermission;
import com.spotpobre.backend.domain.artist.port.ArtistAccountRepository;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevokeArtistAccountServiceTest {

    @Mock
    private ArtistAccountRepository artistAccountRepository;

    @InjectMocks
    private RevokeArtistAccountService revokeArtistAccountService;

    private final ArtistId artistId = ArtistId.generate();
    private final UUID targetUserId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @Test
    void adminRevokesExistingMembership() {
        when(artistAccountRepository.find(artistId, targetUserId))
                .thenReturn(Optional.of(ArtistAccount.manager(artistId, targetUserId, Instant.now())));

        revokeArtistAccountService.revoke(
                new RevokeArtistAccountUseCase.RevokeArtistAccountCommand(true, artistId, targetUserId));

        verify(artistAccountRepository).delete(artistId, targetUserId);
    }

    @Test
    void nonAdminIsForbidden() {
        assertThrows(ForbiddenException.class, () -> revokeArtistAccountService.revoke(
                new RevokeArtistAccountUseCase.RevokeArtistAccountCommand(false, artistId, targetUserId)));
        verify(artistAccountRepository, never()).delete(artistId, targetUserId);
    }

    @Test
    void unknownMembershipFails() {
        when(artistAccountRepository.find(artistId, targetUserId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> revokeArtistAccountService.revoke(
                new RevokeArtistAccountUseCase.RevokeArtistAccountCommand(true, artistId, targetUserId)));
    }

    @Test
    void cannotRevokeLastOwner() {
        when(artistAccountRepository.find(artistId, targetUserId))
                .thenReturn(Optional.of(ArtistAccount.owner(artistId, targetUserId, Instant.now())));
        when(artistAccountRepository.findByArtistId(artistId))
                .thenReturn(List.of(ArtistAccount.owner(artistId, targetUserId, Instant.now())));

        assertThrows(IllegalStateException.class, () -> revokeArtistAccountService.revoke(
                new RevokeArtistAccountUseCase.RevokeArtistAccountCommand(true, artistId, targetUserId)));
        verify(artistAccountRepository, never()).delete(artistId, targetUserId);
    }

    @Test
    void canRevokeOwnerWhenAnotherOwnerRemains() {
        when(artistAccountRepository.find(artistId, targetUserId))
                .thenReturn(Optional.of(ArtistAccount.owner(artistId, targetUserId, Instant.now())));
        when(artistAccountRepository.findByArtistId(artistId))
                .thenReturn(List.of(
                        ArtistAccount.owner(artistId, targetUserId, Instant.now()),
                        ArtistAccount.owner(artistId, otherUserId, Instant.now())));

        revokeArtistAccountService.revoke(
                new RevokeArtistAccountUseCase.RevokeArtistAccountCommand(true, artistId, targetUserId));

        verify(artistAccountRepository).delete(artistId, targetUserId);
    }
}
