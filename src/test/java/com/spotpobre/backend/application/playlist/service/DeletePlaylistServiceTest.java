package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.DeletePlaylistUseCase;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeletePlaylistServiceTest {

    @Mock
    private PlaylistRepository playlistRepository;

    @InjectMocks
    private DeletePlaylistService deletePlaylistService;

    @Test
    void shouldDeletePlaylistSuccessfully() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        UserId ownerId = UserId.generate();
        Playlist existingPlaylist = Playlist.create("My Playlist", ownerId);

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(existingPlaylist));

        deletePlaylistService.deletePlaylist(
                new DeletePlaylistUseCase.DeletePlaylistCommand(playlistId, ownerId));

        verify(playlistRepository, times(1)).findById(playlistId);
        verify(playlistRepository, times(1)).deleteById(playlistId);
    }

    @Test
    void shouldThrowForbiddenWhenCurrentUserIsNotOwner() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        Playlist existingPlaylist = Playlist.create("My Playlist", UserId.generate());
        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(existingPlaylist));

        assertThrows(ForbiddenException.class, () -> deletePlaylistService.deletePlaylist(
                new DeletePlaylistUseCase.DeletePlaylistCommand(playlistId, UserId.generate())));

        verify(playlistRepository, never()).deleteById(any());
    }

    @Test
    void shouldThrowExceptionWhenPlaylistToDeleteNotFound() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            deletePlaylistService.deletePlaylist(
                    new DeletePlaylistUseCase.DeletePlaylistCommand(playlistId, UserId.generate()));
        });

        assertEquals("Playlist not found", exception.getMessage());
        verify(playlistRepository, never()).deleteById(any());
    }
}
