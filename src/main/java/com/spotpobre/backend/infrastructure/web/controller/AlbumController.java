package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.album.port.in.CreateAlbumUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.infrastructure.web.dto.request.CreateAlbumRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.AlbumResponse;
import com.spotpobre.backend.infrastructure.web.mapper.AlbumApiMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final CreateAlbumUseCase createAlbumUseCase;
    private final AlbumApiMapper mapper;

    @PostMapping
    public ResponseEntity<AlbumResponse> createAlbum(@RequestBody @Valid final CreateAlbumRequest request) {
        final var command = mapper.toCommand(request);
        final Album album = createAlbumUseCase.createAlbum(command);
        final AlbumResponse response = mapper.toResponse(album);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
