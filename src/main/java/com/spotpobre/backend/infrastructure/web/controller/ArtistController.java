package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.artist.port.in.CreateArtistIdempotentlyUseCase;
import com.spotpobre.backend.application.artist.port.in.CreateArtistIdempotentlyUseCase.CreateArtistOutcome;
import com.spotpobre.backend.application.artist.port.in.SearchArtistsUseCase;
import com.spotpobre.backend.application.user.port.in.GetCurrentUserUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.infrastructure.web.dto.request.CreateArtistRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.ArtistResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.PageResponse;
import com.spotpobre.backend.infrastructure.web.mapper.ArtistApiMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final CreateArtistIdempotentlyUseCase createArtistIdempotentlyUseCase;
    private final SearchArtistsUseCase searchArtistsUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final ArtistApiMapper mapper;

    /**
     * Artist creation requires a durable {@code Idempotency-Key} (spec §4.3). The authenticated
     * admin scopes the claim, so the same key from a different admin is a different operation.
     */
    @PostMapping
    public ResponseEntity<ArtistResponse> createArtist(
            @RequestHeader(value = "Idempotency-Key", required = false) final String idempotencyKey,
            final Principal principal,
            @RequestBody @Valid final CreateArtistRequest request) {
        final var command = new CreateArtistIdempotentlyUseCase.CreateArtistCommand(
                request.name(), request.ownerUserId());
        final CreateArtistOutcome outcome = createArtistIdempotentlyUseCase.createArtistIdempotently(
                idempotencyKey,
                getCurrentUserUseCase.getCurrentUserId(principal.getName()),
                command);

        // Original success status is preserved on replay (creation responds 201).
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", String.valueOf(outcome.replayed()))
                .body(mapper.toResponse(outcome.artist()));
    }

    @GetMapping("/search")
    public ResponseEntity<PageResponse<ArtistResponse>> searchArtists(
            @RequestParam("query") final String query,
            @RequestParam(defaultValue = "20") final int limit,
            @RequestParam(required = false) final String cursor
    ) {
        final var command = new SearchArtistsUseCase.SearchArtistsCommand(
                query,
                PageRequest.of(0, limit),
                cursor
        );
        final PageResult<Artist> artistPage = searchArtistsUseCase.searchArtists(command);
        return ResponseEntity.ok(mapper.toPageResponse(artistPage));
    }
}
