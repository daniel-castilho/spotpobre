package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.RemoveSongFromPlaylistUseCase;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoveSongFromPlaylistServiceTest {

    @Mock
    private PlaylistRepository playlistRepository;

    @InjectMocks
    private RemoveSongFromPlaylistService removeSongFromPlaylistService;

    @Test
    void shouldRemoveSongFromPlaylistSuccessfully() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        SongId songIdToRemove = new SongId(UUID.randomUUID());
        UserId ownerId = UserId.generate();
        RemoveSongFromPlaylistUseCase.RemoveSongFromPlaylistCommand command =
                new RemoveSongFromPlaylistUseCase.RemoveSongFromPlaylistCommand(
                        playlistId, songIdToRemove, ownerId);

        Playlist existingPlaylist = Playlist.create("My Playlist", ownerId);

        Song songToRemove = Song.create("Song to Remove", new AlbumId(UUID.randomUUID()), "storage-id");
        songToRemove.setId(songIdToRemove);

        existingPlaylist.addSong(songToRemove);

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(existingPlaylist));

        Playlist updatedPlaylist = removeSongFromPlaylistService.removeSongFromPlaylist(command);

        assertNotNull(updatedPlaylist);
        assertTrue(updatedPlaylist.getSongs().isEmpty());
        verify(playlistRepository, times(1)).update(updatedPlaylist);
    }

    @Test
    void shouldThrowForbiddenWhenCurrentUserIsNotOwner() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        SongId songId = new SongId(UUID.randomUUID());
        Playlist existingPlaylist = Playlist.create("My Playlist", UserId.generate());
        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(existingPlaylist));

        RemoveSongFromPlaylistUseCase.RemoveSongFromPlaylistCommand command =
                new RemoveSongFromPlaylistUseCase.RemoveSongFromPlaylistCommand(
                        playlistId, songId, UserId.generate());

        assertThrows(ForbiddenException.class, () ->
                removeSongFromPlaylistService.removeSongFromPlaylist(command));
        verify(playlistRepository, never()).update(any());
    }

    @Test
    void shouldThrowExceptionWhenPlaylistToRemoveFromNotFound() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        SongId songId = new SongId(UUID.randomUUID());
        RemoveSongFromPlaylistUseCase.RemoveSongFromPlaylistCommand command =
                new RemoveSongFromPlaylistUseCase.RemoveSongFromPlaylistCommand(
                        playlistId, songId, UserId.generate());

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            removeSongFromPlaylistService.removeSongFromPlaylist(command);
        });

        assertEquals("Playlist not found", exception.getMessage());
        verify(playlistRepository, never()).update(any());
    }
}
