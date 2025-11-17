package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.song.port.in.GetSongMetadataUseCase;
import com.spotpobre.backend.application.song.port.in.GetSongStreamUrlUseCase;
import com.spotpobre.backend.application.song.port.in.UploadSongUseCase;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.infrastructure.web.dto.request.UploadSongRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.SongDetailsResponse;
import com.spotpobre.backend.infrastructure.web.mapper.SongApiMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/songs")
@RequiredArgsConstructor
public class SongController {

    private final UploadSongUseCase uploadSongUseCase;
    private final GetSongMetadataUseCase getSongMetadataUseCase;
    private final GetSongStreamUrlUseCase getSongStreamUrlUseCase;
    private final SongApiMapper mapper;

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<SongDetailsResponse> uploadSong(
            @RequestPart("request") @Valid final UploadSongRequest request,
            @RequestPart("file") @Valid final MultipartFile file
    ) throws IOException {
        final var command = mapper.toCommand(request);
        final var uploadCommand = new UploadSongUseCase.UploadSongCommand(
                command.title(),
                command.artistId(),
                file.getBytes(),
                file.getContentType()
        );
        final Song uploadedSong = uploadSongUseCase.uploadSong(uploadCommand);
        final URI streamingUrl = getSongStreamUrlUseCase.getSongStreamUrl(uploadedSong.getId());
        final SongDetailsResponse response = mapper.toResponse(uploadedSong, streamingUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{songId}")
    public ResponseEntity<SongDetailsResponse> getSongDetails(@PathVariable final UUID songId) {
        final Song song = getSongMetadataUseCase.getSongMetadata(new SongId(songId));
        final URI streamingUrl = getSongStreamUrlUseCase.getSongStreamUrl(song.getId());
        final SongDetailsResponse response = mapper.toResponse(song, streamingUrl);
        return ResponseEntity.ok(response);
    }
}
