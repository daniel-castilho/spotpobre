package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.album.port.in.CreateAlbumUseCase;
import com.spotpobre.backend.application.song.port.in.ConfirmSongUploadUseCase;
import com.spotpobre.backend.application.song.port.in.InitiateSongUploadUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.song.model.CompletedUploadPart;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/albums")
@RequiredArgsConstructor
@Tag(name = "Albums", description = "Album catalog and artist song uploads")
public class AlbumController {

    private final CreateAlbumUseCase createAlbumUseCase;
    private final InitiateSongUploadUseCase initiateSongUploadUseCase;
    private final ConfirmSongUploadUseCase confirmSongUploadUseCase;
    private final AlbumApiMapper albumApiMapper;
    private final SongApiMapper songApiMapper;

    @PostMapping
    @Operation(summary = "Create an album")
    public ResponseEntity<AlbumResponse> createAlbum(@RequestBody @Valid final CreateAlbumRequest request) {
        final var command = albumApiMapper.toCommand(request);
        final Album album = createAlbumUseCase.createAlbum(command);
        final AlbumResponse response = albumApiMapper.toResponse(album);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
            @PathVariable final UUID albumId,
            @RequestBody @Valid final InitiateSongUploadRequest request
    ) {
        final var command = new InitiateSongUploadUseCase.InitiateSongUploadCommand(
                request.title(),
                new AlbumId(albumId),
                request.contentType(),
                request.contentLengthBytes()
        );
        final var result = initiateSongUploadUseCase.initiateUpload(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(songApiMapper.toInitiateResponse(result));
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
            @RequestBody @Valid final ConfirmSongUploadRequest request
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
                        parts
                )
        );
        return ResponseEntity.ok(songApiMapper.toSongResponse(song));
    }
}
