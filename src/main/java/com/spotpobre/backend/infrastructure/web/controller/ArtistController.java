package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.artist.port.in.CreateArtistUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.infrastructure.web.dto.request.CreateArtistRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.ArtistResponse;
import com.spotpobre.backend.infrastructure.web.mapper.ArtistApiMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final CreateArtistUseCase createArtistUseCase;
    private final ArtistApiMapper mapper;

    @PostMapping
    public ResponseEntity<ArtistResponse> createArtist(@RequestBody @Valid final CreateArtistRequest request) {
        final var command = mapper.toCommand(request);
        final Artist artist = createArtistUseCase.createArtist(command);
        final ArtistResponse response = mapper.toResponse(artist);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
