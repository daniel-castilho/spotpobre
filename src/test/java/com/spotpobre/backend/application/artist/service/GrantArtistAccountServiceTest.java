package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.GrantArtistAccountUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.model.ArtistPermission;
import com.spotpobre.backend.domain.artist.port.ArtistAccountRepository;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private GrantArtistAccountService grantArtistAccountService;

    private final ArtistId artistId = ArtistId.generate();
    private final UUID targetUserId = UUID.randomUUID();

    @Test
    void adminGrantsManagerMembership() {
        when(artistRepository.findById(artistId)).thenReturn(Optional.of(Artist.create("Artist")));

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
}
