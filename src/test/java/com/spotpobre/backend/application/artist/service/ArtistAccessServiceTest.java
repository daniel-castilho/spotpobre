package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.RequireArtistAccessUseCase;
import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.model.ArtistPermission;
import com.spotpobre.backend.domain.artist.port.ArtistAccountRepository;
import com.spotpobre.backend.domain.common.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtistAccessServiceTest {

    @Mock
    private ArtistAccountRepository artistAccountRepository;

    @InjectMocks
    private ArtistAccessService artistAccessService;

    private final ArtistId artistId = ArtistId.generate();
    private final UUID userId = UUID.randomUUID();

    @Test
    void adminBypassesMembershipCheck() {
        assertDoesNotThrow(() -> artistAccessService.requireAccess(
                new RequireArtistAccessUseCase.ActorArtistRef(userId, true), artistId));
    }

    @Test
    void memberWithOwnerPermissionHasAccess() {
        when(artistAccountRepository.find(artistId, userId))
                .thenReturn(Optional.of(ArtistAccount.owner(artistId, userId, Instant.now())));
        assertDoesNotThrow(() -> artistAccessService.requireAccess(
                new RequireArtistAccessUseCase.ActorArtistRef(userId, false), artistId));
    }

    @Test
    void memberWithManagerPermissionHasAccess() {
        when(artistAccountRepository.find(artistId, userId))
                .thenReturn(Optional.of(ArtistAccount.manager(artistId, userId, Instant.now())));
        assertDoesNotThrow(() -> artistAccessService.requireAccess(
                new RequireArtistAccessUseCase.ActorArtistRef(userId, false), artistId));
    }

    @Test
    void nonMemberIsForbidden() {
        when(artistAccountRepository.find(artistId, userId)).thenReturn(Optional.empty());
        assertThrows(ForbiddenException.class, () -> artistAccessService.requireAccess(
                new RequireArtistAccessUseCase.ActorArtistRef(userId, false), artistId));
    }
}
