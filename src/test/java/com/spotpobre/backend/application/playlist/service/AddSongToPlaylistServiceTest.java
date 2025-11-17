package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.AddSongToPlaylistUseCase;
import com.spotpobre.backend.domain.artist.model.ArtistId; // Import ArtistId
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
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
class AddSongToPlaylistServiceTest {

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private SongMetadataRepository songMetadataRepository;

    @InjectMocks
    private AddSongToPlaylistService addSongToPlaylistService;

    @Test
    void shouldAddSongToPlaylistSuccessfully() {
        // Given
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        SongId songId = new SongId(UUID.randomUUID());
        AddSongToPlaylistUseCase.AddSongToPlaylistCommand command = new AddSongToPlaylistUseCase.AddSongToPlaylistCommand(playlistId, songId);

        Playlist playlist = Playlist.create("My Playlist", UserId.generate());
        // Corrected: Provide a valid ArtistId when creating the song
        Song song = Song.create("My Song", new ArtistId(UUID.randomUUID()), "storage-id");

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
        when(songMetadataRepository.findById(songId)).thenReturn(Optional.of(song));

        // When
        Playlist updatedPlaylist = addSongToPlaylistService.addSongToPlaylist(command);

        // Then
        assertNotNull(updatedPlaylist);
        assertEquals(1, updatedPlaylist.getSongs().size());
        assertTrue(updatedPlaylist.getSongs().contains(song));
        verify(playlistRepository, times(1)).save(playlist);
    }

    @Test
    void shouldThrowExceptionWhenPlaylistNotFound() {
        // Given
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        SongId songId = new SongId(UUID.randomUUID());
        AddSongToPlaylistUseCase.AddSongToPlaylistCommand command = new AddSongToPlaylistUseCase.AddSongToPlaylistCommand(playlistId, songId);

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.empty());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            addSongToPlaylistService.addSongToPlaylist(command);
        });

        assertEquals("Playlist not found", exception.getMessage());
        verify(songMetadataRepository, never()).findById(any());
        verify(playlistRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenSongNotFound() {
        // Given
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        SongId songId = new SongId(UUID.randomUUID());
        AddSongToPlaylistUseCase.AddSongToPlaylistCommand command = new AddSongToPlaylistUseCase.AddSongToPlaylistCommand(playlistId, songId);

        Playlist playlist = Playlist.create("My Playlist", UserId.generate());

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
        when(songMetadataRepository.findById(songId)).thenReturn(Optional.empty());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            addSongToPlaylistService.addSongToPlaylist(command);
        });

        assertEquals("Song not found", exception.getMessage());
        verify(playlistRepository, never()).save(any());
    }
}
