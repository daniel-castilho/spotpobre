package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.RemoveSongFromPlaylistUseCase;
import com.spotpobre.backend.domain.album.model.AlbumId; // Import AlbumId
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoveSongFromPlaylistServiceTest {

    @Mock
    private PlaylistRepository playlistRepository;

    @InjectMocks
    private RemoveSongFromPlaylistService removeSongFromPlaylistService;

    @Test
    void shouldRemoveSongFromPlaylistSuccessfully() {
        // Given
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        SongId songIdToRemove = new SongId(UUID.randomUUID());
        RemoveSongFromPlaylistUseCase.RemoveSongFromPlaylistCommand command = new RemoveSongFromPlaylistUseCase.RemoveSongFromPlaylistCommand(playlistId, songIdToRemove);

        Playlist existingPlaylist = Playlist.create("My Playlist", UserId.generate());
        
        // Corrected: Provide a valid AlbumId when creating the song
        Song songToRemove = Song.create("Song to Remove", new AlbumId(UUID.randomUUID()), "storage-id");
        songToRemove.setId(songIdToRemove);
        
        existingPlaylist.addSong(songToRemove);

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(existingPlaylist));

        // When
        Playlist updatedPlaylist = removeSongFromPlaylistService.removeSongFromPlaylist(command);

        // Then
        assertNotNull(updatedPlaylist);
        assertTrue(updatedPlaylist.getSongs().isEmpty());
        verify(playlistRepository, times(1)).save(updatedPlaylist);
    }

    @Test
    void shouldThrowExceptionWhenPlaylistToRemoveFromNotFound() {
        // Given
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        SongId songId = new SongId(UUID.randomUUID());
        RemoveSongFromPlaylistUseCase.RemoveSongFromPlaylistCommand command = new RemoveSongFromPlaylistUseCase.RemoveSongFromPlaylistCommand(playlistId, songId);

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.empty());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            removeSongFromPlaylistService.removeSongFromPlaylist(command);
        });

        assertEquals("Playlist not found", exception.getMessage());
        verify(playlistRepository, never()).save(any());
    }
}
