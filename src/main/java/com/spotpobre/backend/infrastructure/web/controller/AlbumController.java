package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.album.port.in.CreateAlbumIdempotentlyUseCase;
import com.spotpobre.backend.application.album.port.in.CreateAlbumIdempotentlyUseCase.CreateAlbumOutcome;
import com.spotpobre.backend.application.song.port.in.ConfirmSongUploadUseCase;
import com.spotpobre.backend.application.song.port.in.InitiateSongUploadIdempotentlyUseCase;
import com.spotpobre.backend.application.user.port.in.GetCurrentUserUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.song.model.CompletedUploadPart;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.user.model.Role;
import com.spotpobre.backend.infrastructure.web.dto.request.ConfirmSongUploadRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.CreateAlbumRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.InitiateSongUploadRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.AlbumResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.InitiateSongUploadResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.SongResponse;
import com.spotpobre.backend.infrastructure.web.mapper.AlbumApiMapper;
import com.spotpobre.backend.infrastructure.web.mapper.SongApiMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/albums")
@RequiredArgsConstructor
@Tag(name = "Albums", description = "Album catalog and artist song uploads")
public class AlbumController {

    private final CreateAlbumIdempotentlyUseCase createAlbumIdempotentlyUseCase;
    private final InitiateSongUploadIdempotentlyUseCase initiateSongUploadIdempotentlyUseCase;
    private final ConfirmSongUploadUseCase confirmSongUploadUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final AlbumApiMapper albumApiMapper;
    private final SongApiMapper songApiMapper;

    /**
     * Album creation requires a durable {@code Idempotency-Key} (spec §4.3). The authenticated
     * actor scopes the claim; membership authorization is re-checked before the claim on every
     * call, including replays.
     */
    @PostMapping
    @Operation(summary = "Create an album")
    public ResponseEntity<AlbumResponse> createAlbum(
            @RequestHeader(value = "Idempotency-Key", required = false) final String idempotencyKey,
            @RequestBody @Valid final CreateAlbumRequest request,
            final Authentication authentication
    ) {
        final UUID actorUserId = currentUserId(authentication);
        final var command = new CreateAlbumIdempotentlyUseCase.CreateAlbumCommand(
                request.name(),
                request.artistId() == null ? null : new ArtistId(request.artistId()),
                request.coverArtUrl(),
                actorUserId,
                isAdmin(authentication)
        );
        final CreateAlbumOutcome outcome =
                createAlbumIdempotentlyUseCase.createAlbumIdempotently(idempotencyKey, command);

        // Original success status is preserved on replay (creation responds 201).
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", String.valueOf(outcome.replayed()))
                .body(albumApiMapper.toResponse(outcome.album()));
    }

    @PostMapping("/{albumId}/songs")
    @Operation(
            summary = "Request a presigned URL to upload a song",
            description = """
                    Artist-only. Validates album existence, content type and file size, then returns \
                    short-lived presigned URL(s) so the client can PUT the audio file directly to object \
                    storage. Files larger than 100 MB receive multipart part URLs. After the client \
                    finishes uploading, call the confirm endpoint.
                    """
    )
    public ResponseEntity<InitiateSongUploadResponse> initiateSongUpload(
            @RequestHeader(value = "Idempotency-Key", required = false) final String idempotencyKey,
            @PathVariable final UUID albumId,
            @RequestBody @Valid final InitiateSongUploadRequest request,
            final Authentication authentication
    ) {
        final var command = new InitiateSongUploadIdempotentlyUseCase.InitiateSongUploadCommand(
                request.title(),
                new AlbumId(albumId),
                request.contentType(),
                request.contentLengthBytes(),
                currentUserId(authentication),
                isAdmin(authentication)
        );
        final var result =
                initiateSongUploadIdempotentlyUseCase.initiateUploadIdempotently(idempotencyKey, command);

        // Original success status is preserved on replay (initiation responds 201); the presigned
        // URL is always freshly signed and targets the staging key bound to the staged upload.
        // No Song row exists yet — the song becomes visible only after confirmation.
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", String.valueOf(result.replayed()))
                .body(songApiMapper.toStagedInitiateResponse(result));
    }

    @PostMapping("/{albumId}/songs/{songId}/confirm")
    @Operation(
            summary = "Confirm a completed song upload",
            description = """
                    Artist-only. Verifies the object exists in storage (or completes an S3 multipart \
                    upload using the part ETags) and keeps the song metadata created at initiate time.
                    """
    )
    public ResponseEntity<SongResponse> confirmSongUpload(
            @PathVariable final UUID albumId,
            @PathVariable final UUID songId,
            @RequestBody @Valid final ConfirmSongUploadRequest request,
            final Authentication authentication
    ) {
        final List<CompletedUploadPart> parts = request.parts() == null
                ? List.of()
                : request.parts().stream()
                .map(part -> new CompletedUploadPart(part.partNumber(), part.eTag()))
                .toList();

        final Song song = confirmSongUploadUseCase.confirmUpload(
                new ConfirmSongUploadUseCase.ConfirmSongUploadCommand(
                        new SongId(songId),
                        new AlbumId(albumId),
                        request.storageKey(),
                        request.multipartUploadId(),
                        parts,
                        currentUserId(authentication),
                        isAdmin(authentication)
                )
        );
        return ResponseEntity.ok(songApiMapper.toSongResponse(song));
    }

    private UUID currentUserId(final Authentication authentication) {
        return getCurrentUserUseCase.getCurrentUserId(authentication.getName()).value();
    }

    private static boolean isAdmin(final Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + Role.ADMIN.name()).equals(authority.getAuthority()));
    }
}
