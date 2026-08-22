package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.CreateArtistUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistPermission;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.user.model.Role;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateArtistServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CreateArtistService createArtistService;

    private User existingUser() {
        return User.builder()
                .id(new UserId(UUID.randomUUID()))
                .profile(new UserProfile("Owner", "owner@example.com", "BR"))
                .password("hash")
                .roles(Set.of(Role.ARTIST))
                .build();
    }

    @Test
    void shouldCreateArtistWithOwnerMembershipAtomically() {
        // Given
        UUID ownerUserId = UUID.randomUUID();
        when(userRepository.findById(new UserId(ownerUserId))).thenReturn(Optional.of(existingUser()));
        CreateArtistUseCase.CreateArtistCommand command =
                new CreateArtistUseCase.CreateArtistCommand("New Artist", ownerUserId);

        // When
        Artist createdArtist = createArtistService.createArtist(command);

        // Then
        assertNotNull(createdArtist);
        assertEquals("New Artist", createdArtist.getName());

        ArgumentCaptor<Artist> artistCaptor = ArgumentCaptor.forClass(Artist.class);
        ArgumentCaptor<ArtistAccount> accountCaptor = ArgumentCaptor.forClass(ArtistAccount.class);
        verify(artistRepository, times(1)).createWithOwner(artistCaptor.capture(), accountCaptor.capture());
        verify(artistRepository, never()).save(any());

        assertEquals(createdArtist.getId(), artistCaptor.getValue().getId());
        assertEquals(ownerUserId, accountCaptor.getValue().userId());
        assertEquals(ArtistPermission.OWNER, accountCaptor.getValue().permission());
        assertEquals(createdArtist.getId(), accountCaptor.getValue().artistId());
    }

    @Test
    void shouldThrowWhenOwnerUserDoesNotExist() {
        // Given
        UUID missingUserId = UUID.randomUUID();
        when(userRepository.findById(new UserId(missingUserId))).thenReturn(Optional.empty());
        CreateArtistUseCase.CreateArtistCommand command =
                new CreateArtistUseCase.CreateArtistCommand("Orphan Artist", missingUserId);

        // When & Then
        assertThrows(NotFoundException.class, () -> createArtistService.createArtist(command));
        verify(artistRepository, never()).createWithOwner(any(), any());
    }

    @Test
    void shouldRejectCommandWithoutOwner() {
        assertThrows(NullPointerException.class,
                () -> createArtistService.createArtist(new CreateArtistUseCase.CreateArtistCommand("No Owner", null)));
    }
}
