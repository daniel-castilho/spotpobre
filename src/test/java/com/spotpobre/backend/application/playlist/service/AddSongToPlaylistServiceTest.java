package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.application.playlist.port.in.AddSongToPlaylistUseCase;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistConcurrentModificationException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        SongId songId = new SongId(UUID.randomUUID());
        UserId ownerId = UserId.generate();
        AddSongToPlaylistUseCase.AddSongToPlaylistCommand command =
                new AddSongToPlaylistUseCase.AddSongToPlaylistCommand(playlistId, songId, ownerId);

        Playlist playlist = Playlist.create("My Playlist", ownerId);
        Song song = Song.create("My Song", new AlbumId(UUID.randomUUID()), "storage-id");

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
        when(songMetadataRepository.findById(songId)).thenReturn(Optional.of(song));

        Playlist updatedPlaylist = addSongToPlaylistService.addSongToPlaylist(command);

        assertNotNull(updatedPlaylist);
        assertEquals(1, updatedPlaylist.getSongs().size());
        assertTrue(updatedPlaylist.getSongs().contains(song));
        verify(playlistRepository, times(1)).update(playlist);
    }

    @Test
    void shouldBeNoOpWithoutWriteWhenSongAlreadyPresent() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        UserId ownerId = UserId.generate();
        Playlist playlist = Playlist.create("My Playlist", ownerId);
        Song song = Song.create("My Song", new AlbumId(UUID.randomUUID()), "storage-id");
        playlist.ensureSongPresent(song);

        AddSongToPlaylistUseCase.AddSongToPlaylistCommand command =
                new AddSongToPlaylistUseCase.AddSongToPlaylistCommand(playlistId, song.getId(), ownerId);

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
        when(songMetadataRepository.findById(song.getId())).thenReturn(Optional.of(song));

        Playlist result = addSongToPlaylistService.addSongToPlaylist(command);

        assertEquals(1, result.getSongs().size());
        verify(playlistRepository, never()).update(any());
    }

    @Test
    void shouldSucceedWhenConcurrentSameSongPutAlreadyConverged() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        SongId songId = new SongId(UUID.randomUUID());
        UserId ownerId = UserId.generate();
        AddSongToPlaylistUseCase.AddSongToPlaylistCommand command =
                new AddSongToPlaylistUseCase.AddSongToPlaylistCommand(playlistId, songId, ownerId);

        Playlist staleSnapshot = Playlist.create("My Playlist", ownerId);
        Song song = Song.create("My Song", new AlbumId(UUID.randomUUID()), "storage-id");
        song.setId(songId);
        Playlist concurrentWinner = Playlist.create("My Playlist", ownerId);
        concurrentWinner.ensureSongPresent(song);

        when(playlistRepository.findById(playlistId))
                .thenReturn(Optional.of(staleSnapshot))
                .thenReturn(Optional.of(concurrentWinner));
        when(songMetadataRepository.findById(songId)).thenReturn(Optional.of(song));
        doThrow(new PlaylistConcurrentModificationException(playlistId))
                .when(playlistRepository).update(staleSnapshot);

        Playlist result = addSongToPlaylistService.addSongToPlaylist(command);

        assertTrue(result.containsSong(songId), "Reloaded playlist must contain the desired membership");
        assertEquals(concurrentWinner.getVersion(), result.getVersion());
    }

    @Test
    void shouldThrowWhenConcurrentModificationDoesNotContainDesiredMembership() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        SongId songId = new SongId(UUID.randomUUID());
        UserId ownerId = UserId.generate();
        AddSongToPlaylistUseCase.AddSongToPlaylistCommand command =
                new AddSongToPlaylistUseCase.AddSongToPlaylistCommand(playlistId, songId, ownerId);

        Playlist staleSnapshot = Playlist.create("My Playlist", ownerId);
        Song song = Song.create("My Song", new AlbumId(UUID.randomUUID()), "storage-id");
        Playlist genuinelyDifferent = Playlist.create("My Playlist", ownerId);

        when(playlistRepository.findById(playlistId))
                .thenReturn(Optional.of(staleSnapshot))
                .thenReturn(Optional.of(genuinelyDifferent));
        when(songMetadataRepository.findById(songId)).thenReturn(Optional.of(song));
        doThrow(new PlaylistConcurrentModificationException(playlistId))
                .when(playlistRepository).update(staleSnapshot);

        assertThrows(PlaylistConcurrentModificationException.class,
                () -> addSongToPlaylistService.addSongToPlaylist(command));
    }

    @Test
    void shouldThrowForbiddenWhenCurrentUserIsNotOwner() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        SongId songId = new SongId(UUID.randomUUID());
        Playlist playlist = Playlist.create("My Playlist", UserId.generate());
        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));

        AddSongToPlaylistUseCase.AddSongToPlaylistCommand command =
                new AddSongToPlaylistUseCase.AddSongToPlaylistCommand(playlistId, songId, UserId.generate());

        assertThrows(ForbiddenException.class, () -> addSongToPlaylistService.addSongToPlaylist(command));
        verify(songMetadataRepository, never()).findById(any());
        verify(playlistRepository, never()).update(any());
    }

    @Test
    void shouldThrowExceptionWhenPlaylistNotFound() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        SongId songId = new SongId(UUID.randomUUID());
        AddSongToPlaylistUseCase.AddSongToPlaylistCommand command =
                new AddSongToPlaylistUseCase.AddSongToPlaylistCommand(playlistId, songId, UserId.generate());

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            addSongToPlaylistService.addSongToPlaylist(command);
        });

        assertEquals("Playlist not found", exception.getMessage());
        verify(songMetadataRepository, never()).findById(any());
        verify(playlistRepository, never()).update(any());
    }

    @Test
    void shouldThrowExceptionWhenSongNotFound() {
        PlaylistId playlistId = new PlaylistId(UUID.randomUUID());
        SongId songId = new SongId(UUID.randomUUID());
        UserId ownerId = UserId.generate();
        AddSongToPlaylistUseCase.AddSongToPlaylistCommand command =
                new AddSongToPlaylistUseCase.AddSongToPlaylistCommand(playlistId, songId, ownerId);

        Playlist playlist = Playlist.create("My Playlist", ownerId);

        when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
        when(songMetadataRepository.findById(songId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            addSongToPlaylistService.addSongToPlaylist(command);
        });

        assertEquals("Song not found", exception.getMessage());
        verify(playlistRepository, never()).update(any());
    }
}
