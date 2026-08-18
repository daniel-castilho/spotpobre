package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.artist.port.in.CreateArtistUseCase;
import com.spotpobre.backend.application.artist.port.in.SearchArtistsUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.infrastructure.web.dto.request.CreateArtistRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.ArtistResponse;
import com.spotpobre.backend.infrastructure.web.mapper.ArtistApiMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final CreateArtistUseCase createArtistUseCase;
    private final SearchArtistsUseCase searchArtistsUseCase;
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
        final var command = new SearchArtistsUseCase.SearchArtistsCommand(
                query,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
        );
        final PageResult<Artist> artistPage = searchArtistsUseCase.searchArtists(command);
        return ResponseEntity.ok(mapper.toResponsePage(artistPage, pageable));
    }
}
