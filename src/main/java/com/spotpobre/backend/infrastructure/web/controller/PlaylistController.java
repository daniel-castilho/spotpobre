package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.playlist.port.in.AddSongToPlaylistUseCase;
import com.spotpobre.backend.application.playlist.port.in.CreatePlaylistUseCase;
import com.spotpobre.backend.application.playlist.port.in.DeletePlaylistUseCase;
import com.spotpobre.backend.application.playlist.port.in.GetPlaylistDetailsUseCase;
import com.spotpobre.backend.application.playlist.port.in.GetPlaylistsByOwnerUseCase;
import com.spotpobre.backend.application.playlist.port.in.RemoveSongFromPlaylistUseCase;
import com.spotpobre.backend.application.playlist.port.in.UpdatePlaylistDetailsUseCase;
import com.spotpobre.backend.application.user.port.in.GetCurrentUserUseCase;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.infrastructure.web.dto.request.CreatePlaylistRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.UpdatePlaylistRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.PageResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.PlaylistResponse;
import com.spotpobre.backend.infrastructure.web.mapper.PlaylistApiMapper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PlaylistController {

    private final CreatePlaylistUseCase createPlaylistUseCase;
    private final GetPlaylistDetailsUseCase getPlaylistDetailsUseCase;
    private final AddSongToPlaylistUseCase addSongToPlaylistUseCase;
    private final GetPlaylistsByOwnerUseCase getPlaylistsByOwnerUseCase;
    private final UpdatePlaylistDetailsUseCase updatePlaylistDetailsUseCase;
    private final RemoveSongFromPlaylistUseCase removeSongFromPlaylistUseCase;
    private final DeletePlaylistUseCase deletePlaylistUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final PlaylistApiMapper mapper;

    public PlaylistController(
            final CreatePlaylistUseCase createPlaylistUseCase,
            final GetPlaylistDetailsUseCase getPlaylistDetailsUseCase,
            final AddSongToPlaylistUseCase addSongToPlaylistUseCase,
            final GetPlaylistsByOwnerUseCase getPlaylistsByOwnerUseCase,
            final UpdatePlaylistDetailsUseCase updatePlaylistDetailsUseCase,
            final RemoveSongFromPlaylistUseCase removeSongFromPlaylistUseCase,
            final DeletePlaylistUseCase deletePlaylistUseCase,
            final GetCurrentUserUseCase getCurrentUserUseCase,
            final PlaylistApiMapper mapper
    ) {
        this.createPlaylistUseCase = createPlaylistUseCase;
        this.getPlaylistDetailsUseCase = getPlaylistDetailsUseCase;
        this.addSongToPlaylistUseCase = addSongToPlaylistUseCase;
        this.getPlaylistsByOwnerUseCase = getPlaylistsByOwnerUseCase;
        this.updatePlaylistDetailsUseCase = updatePlaylistDetailsUseCase;
        this.removeSongFromPlaylistUseCase = removeSongFromPlaylistUseCase;
        this.deletePlaylistUseCase = deletePlaylistUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.mapper = mapper;
    }

    @PostMapping("/playlists")
    public ResponseEntity<PlaylistResponse> createPlaylist(
            @RequestBody @Valid final CreatePlaylistRequest request,
            final Principal principal
    ) {
        final UserId ownerId = getCurrentUserUseCase.getCurrentUserId(principal.getName());

        final var command = mapper.toCommand(request, ownerId);
        final Playlist playlist = createPlaylistUseCase.createPlaylist(command);
        final PlaylistResponse response = mapper.toResponse(playlist);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/playlists/{playlistId}")
    public ResponseEntity<PlaylistResponse> updatePlaylistDetails(
            @PathVariable final UUID playlistId,
            @RequestBody @Valid final UpdatePlaylistRequest request,
            final Principal principal
    ) {
        final var command = new UpdatePlaylistDetailsUseCase.UpdatePlaylistDetailsCommand(
                new PlaylistId(playlistId),
                request.name(),
                getCurrentUserUseCase.getCurrentUserId(principal.getName())
        );
        final Playlist updatedPlaylist = updatePlaylistDetailsUseCase.updatePlaylistDetails(command);
        final PlaylistResponse response = mapper.toResponse(updatedPlaylist);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/playlists/{playlistId}")
    public ResponseEntity<Void> deletePlaylist(
            @PathVariable final UUID playlistId,
            final Principal principal
    ) {
        deletePlaylistUseCase.deletePlaylist(new DeletePlaylistUseCase.DeletePlaylistCommand(
                new PlaylistId(playlistId),
                getCurrentUserUseCase.getCurrentUserId(principal.getName())
        ));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/playlists/{playlistId}/songs/{songId}")
    public ResponseEntity<Void> removeSongFromPlaylist(
            @PathVariable final UUID playlistId,
            @PathVariable final UUID songId,
            final Principal principal
    ) {
        final var command = new RemoveSongFromPlaylistUseCase.RemoveSongFromPlaylistCommand(
                new PlaylistId(playlistId),
                new SongId(songId),
                getCurrentUserUseCase.getCurrentUserId(principal.getName())
        );
        removeSongFromPlaylistUseCase.removeSongFromPlaylist(command);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/playlists")
    public ResponseEntity<PageResponse<PlaylistResponse>> getMyPlaylists(
            final Pageable pageable,
            @RequestParam(required = false) final String nextPageToken,
            final Principal principal
    ) {
        final UserId ownerId = getCurrentUserUseCase.getCurrentUserId(principal.getName());

        final PageResult<Playlist> playlistPage = getPlaylistsByOwnerUseCase.getPlaylistsByOwner(
                ownerId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()),
                nextPageToken
        );
        final PageResponse<PlaylistResponse> response = mapper.toPageResponse(playlistPage);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/playlists/{playlistId}")
    public ResponseEntity<PlaylistResponse> getPlaylist(@PathVariable final UUID playlistId) {
        final Playlist playlist = getPlaylistDetailsUseCase.getPlaylistDetails(new PlaylistId(playlistId));
        final PlaylistResponse response = mapper.toResponse(playlist);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/playlists/{playlistId}/songs/{songId}")
    public ResponseEntity<PlaylistResponse> addSongToPlaylist(
            @PathVariable final UUID playlistId,
            @PathVariable final UUID songId,
            final Principal principal
    ) {
        final var command = new AddSongToPlaylistUseCase.AddSongToPlaylistCommand(
                new PlaylistId(playlistId),
                new SongId(songId),
                getCurrentUserUseCase.getCurrentUserId(principal.getName())
        );
        final Playlist playlist = addSongToPlaylistUseCase.addSongToPlaylist(command);
        final PlaylistResponse response = mapper.toResponse(playlist);
        return ResponseEntity.ok(response);
    }
}