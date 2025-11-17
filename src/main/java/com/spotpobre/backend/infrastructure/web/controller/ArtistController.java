package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.artist.port.in.CreateArtistUseCase;
import com.spotpobre.backend.application.artist.port.in.SearchArtistsUseCase; // Import
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.infrastructure.web.dto.request.CreateArtistRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.ArtistResponse;
import com.spotpobre.backend.infrastructure.web.mapper.ArtistApiMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page; // Import
import org.springframework.data.domain.Pageable; // Import
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final CreateArtistUseCase createArtistUseCase;
    private final SearchArtistsUseCase searchArtistsUseCase; // Inject
    private final ArtistApiMapper mapper;

    @PostMapping
    public ResponseEntity<ArtistResponse> createArtist(@RequestBody @Valid final CreateArtistRequest request) {
        final var command = mapper.toCommand(request);
        final Artist artist = createArtistUseCase.createArtist(command);
        final ArtistResponse response = mapper.toResponse(artist);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ArtistResponse>> searchArtists(
            @RequestParam("query") final String query,
            final Pageable pageable
    ) {
        final var command = new SearchArtistsUseCase.SearchArtistsCommand(query, pageable);
        final Page<Artist> artistPage = searchArtistsUseCase.searchArtists(command);
        return ResponseEntity.ok(artistPage.map(mapper::toResponse));
    }
}
