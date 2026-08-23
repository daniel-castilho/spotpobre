package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.artist.port.in.CreateArtistIdempotentlyUseCase;
import com.spotpobre.backend.application.artist.port.in.CreateArtistIdempotentlyUseCase.CreateArtistOutcome;
import com.spotpobre.backend.application.album.port.in.ListAlbumsByArtistUseCase;
import com.spotpobre.backend.application.artist.port.in.ListArtistsUseCase;
import com.spotpobre.backend.application.artist.port.in.SearchArtistsUseCase;
import com.spotpobre.backend.application.user.port.in.GetCurrentUserUseCase;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.infrastructure.web.dto.request.CreateArtistRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.AlbumResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.ArtistResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.PageResponse;
import com.spotpobre.backend.infrastructure.web.mapper.AlbumApiMapper;
import com.spotpobre.backend.infrastructure.web.mapper.ArtistApiMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final CreateArtistIdempotentlyUseCase createArtistIdempotentlyUseCase;
    private final SearchArtistsUseCase searchArtistsUseCase;
    private final ListArtistsUseCase listArtistsUseCase;
    private final ListAlbumsByArtistUseCase listAlbumsByArtistUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final ArtistApiMapper mapper;
    private final AlbumApiMapper albumMapper;

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

    /** Cursor-paginated catalog listing of all artists (storage-native scan order). */
    @GetMapping
    public ResponseEntity<PageResponse<ArtistResponse>> listArtists(
            @RequestParam(defaultValue = "20") final int limit,
            @RequestParam(required = false) final String cursor
    ) {
        final var command = new ListArtistsUseCase.ListArtistsCommand(
                PageRequest.of(0, limit),
                cursor
        );
        return ResponseEntity.ok(mapper.toPageResponse(listArtistsUseCase.listArtists(command)));
    }

    /**
     * Cursor-paginated albums of one artist. Unknown artists answer 404 — the path identifies a
     * resource; known artists without albums answer an empty page.
     */
    @GetMapping("/{artistId}/albums")
    public ResponseEntity<PageResponse<AlbumResponse>> listAlbumsByArtist(
            @PathVariable final UUID artistId,
            @RequestParam(defaultValue = "20") final int limit,
            @RequestParam(required = false) final String cursor
    ) {
        final var command = new ListAlbumsByArtistUseCase.ListAlbumsByArtistCommand(
                new ArtistId(artistId),
                PageRequest.of(0, limit),
                cursor
        );
        return ResponseEntity.ok(albumMapper.toPageResponse(listAlbumsByArtistUseCase.listAlbumsByArtist(command)));
    }
}
