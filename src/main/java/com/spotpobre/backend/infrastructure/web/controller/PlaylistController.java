package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.playlist.port.in.AddSongToPlaylistUseCase;
import com.spotpobre.backend.application.playlist.port.in.CreatePlaylistUseCase;
import com.spotpobre.backend.application.playlist.port.in.GetPlaylistDetailsUseCase;
import com.spotpobre.backend.application.playlist.port.in.GetPlaylistsByOwnerUseCase; // Renamed
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.domain.user.port.UserRepository;
import com.spotpobre.backend.infrastructure.persistence.kv.model.DynamoDbPage;
import com.spotpobre.backend.infrastructure.web.dto.request.CreatePlaylistRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.PageResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.PlaylistResponse;
import com.spotpobre.backend.infrastructure.web.mapper.PlaylistApiMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1") // Changed base path
@RequiredArgsConstructor
public class PlaylistController {

    private final CreatePlaylistUseCase createPlaylistUseCase;
    private final GetPlaylistDetailsUseCase getPlaylistDetailsUseCase;
    private final AddSongToPlaylistUseCase addSongToPlaylistUseCase;
    private final GetPlaylistsByOwnerUseCase getPlaylistsByOwnerUseCase; // Renamed
    private final PlaylistApiMapper mapper;
    private final UserRepository userRepository;

    @PostMapping("/playlists")
    public ResponseEntity<PlaylistResponse> createPlaylist(
            @RequestBody @Valid final CreatePlaylistRequest request,
            final Principal principal
    ) {
        final String userEmail = principal.getName();
        final UserId ownerId = userRepository.findByProfileEmail(userEmail)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        final var command = mapper.toCommand(request, ownerId);
        final Playlist playlist = createPlaylistUseCase.createPlaylist(command);
        final PlaylistResponse response = mapper.toResponse(playlist);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me/playlists") // Changed endpoint
    public ResponseEntity<PageResponse<PlaylistResponse>> getMyPlaylists(
            final Pageable pageable,
            @RequestParam(required = false) final String nextPageToken,
            final Principal principal
    ) {
        final String userEmail = principal.getName();
        final UserId ownerId = userRepository.findByProfileEmail(userEmail)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        final DynamoDbPage<Playlist> playlistPage = getPlaylistsByOwnerUseCase.getPlaylistsByOwner(ownerId, pageable, nextPageToken);
        final PageResponse<PlaylistResponse> response = mapper.toPageResponse(playlistPage);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/playlists/{playlistId}")
    public ResponseEntity<PlaylistResponse> getPlaylist(@PathVariable final UUID playlistId) {
        final Playlist playlist = getPlaylistDetailsUseCase.getPlaylistDetails(new PlaylistId(playlistId));
        final PlaylistResponse response = mapper.toResponse(playlist);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/playlists/{playlistId}/songs/{songId}")
    public ResponseEntity<PlaylistResponse> addSongToPlaylist(
            @PathVariable final UUID playlistId,
            @PathVariable final UUID songId
    ) {
        final var command = new AddSongToPlaylistUseCase.AddSongToPlaylistCommand(
                new PlaylistId(playlistId),
                new SongId(songId)
        );
        final Playlist playlist = addSongToPlaylistUseCase.addSongToPlaylist(command);
        final PlaylistResponse response = mapper.toResponse(playlist);
        return ResponseEntity.ok(response);
    }
}
