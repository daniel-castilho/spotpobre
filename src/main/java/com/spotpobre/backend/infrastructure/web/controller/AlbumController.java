package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.album.port.in.CreateAlbumUseCase;
import com.spotpobre.backend.application.song.port.in.UploadSongUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.infrastructure.web.dto.request.CreateAlbumRequest;
import com.spotpobre.backend.infrastructure.web.dto.request.UploadSongRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.AlbumResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.SongResponse;
import com.spotpobre.backend.infrastructure.web.mapper.AlbumApiMapper;
import com.spotpobre.backend.infrastructure.web.mapper.SongApiMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final CreateAlbumUseCase createAlbumUseCase;
    private final UploadSongUseCase uploadSongUseCase;
    private final AlbumApiMapper albumApiMapper;
    private final SongApiMapper songApiMapper;

    @PostMapping
    public ResponseEntity<AlbumResponse> createAlbum(@RequestBody @Valid final CreateAlbumRequest request) {
        final var command = albumApiMapper.toCommand(request);
        final Album album = createAlbumUseCase.createAlbum(command);
        final AlbumResponse response = albumApiMapper.toResponse(album);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{albumId}/songs")
    public ResponseEntity<SongResponse> uploadSong(
            @PathVariable final UUID albumId,
            @RequestPart("request") @Valid final UploadSongRequest request,
            @RequestPart("file") @Valid final MultipartFile file
    ) throws IOException {
        final var command = new UploadSongUseCase.UploadSongCommand(
                request.title(),
                new AlbumId(albumId),
                file.getBytes(),
                file.getContentType()
        );
        final Song uploadedSong = uploadSongUseCase.uploadSong(command);
        final SongResponse response = songApiMapper.toSongResponse(uploadedSong);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
