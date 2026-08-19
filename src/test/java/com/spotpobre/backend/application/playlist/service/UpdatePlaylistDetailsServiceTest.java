package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.UpdatePlaylistDetailsUseCase;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.user.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdatePlaylistDetailsServiceTest {

    @Mock
    private PlaylistRepository playlistRepository;

    @InjectMocks
    private UpdatePlaylistDetailsService updatePlaylistDetailsService;

    @Test
    void shouldUpdatePlaylistNameSuccessfully() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        UserId ownerId = UserId.generate();
        String newName = "My Updated Rock Playlist";
        UpdatePlaylistDetailsUseCase.UpdatePlaylistDetailsCommand command =
                new UpdatePlaylistDetailsUseCase.UpdatePlaylistDetailsCommand(playlistId, newName, ownerId);

        Playlist existingPlaylist = Playlist.create("Old Name", ownerId);
        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(existingPlaylist));

        Playlist updatedPlaylist = updatePlaylistDetailsService.updatePlaylistDetails(command);

        assertNotNull(updatedPlaylist);
        assertEquals(newName, updatedPlaylist.getName());
        verify(playlistRepository, times(1)).update(updatedPlaylist);
    }

    @Test
    void shouldThrowForbiddenWhenCurrentUserIsNotOwner() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        Playlist existingPlaylist = Playlist.create("Old Name", UserId.generate());
        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(existingPlaylist));

        UpdatePlaylistDetailsUseCase.UpdatePlaylistDetailsCommand command =
                new UpdatePlaylistDetailsUseCase.UpdatePlaylistDetailsCommand(
                        playlistId, "Hijacked", UserId.generate());

        assertThrows(ForbiddenException.class, () -> updatePlaylistDetailsService.updatePlaylistDetails(command));
        verify(playlistRepository, never()).update(any());
    }

    @Test
    void shouldThrowExceptionWhenPlaylistToUpdateNotFound() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        UpdatePlaylistDetailsUseCase.UpdatePlaylistDetailsCommand command =
                new UpdatePlaylistDetailsUseCase.UpdatePlaylistDetailsCommand(
                        playlistId, "New Name", UserId.generate());

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            updatePlaylistDetailsService.updatePlaylistDetails(command);
        });

        assertEquals("Playlist not found", exception.getMessage());
        verify(playlistRepository, never()).update(any());
    }
}
