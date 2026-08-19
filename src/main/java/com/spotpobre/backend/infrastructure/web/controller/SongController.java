package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.song.port.in.GetSongMetadataUseCase;
import com.spotpobre.backend.application.song.port.in.GetSongStreamUrlUseCase;
import com.spotpobre.backend.application.song.port.in.SearchSongsUseCase;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.infrastructure.web.dto.response.PageResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.SongDetailsResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.SongResponse;
import com.spotpobre.backend.infrastructure.web.mapper.SongApiMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/songs")
@RequiredArgsConstructor
public class SongController {

    private final GetSongMetadataUseCase getSongMetadataUseCase;
    private final GetSongStreamUrlUseCase getSongStreamUrlUseCase;
    private final SearchSongsUseCase searchSongsUseCase;
    private final SongApiMapper mapper;

    @GetMapping("/search")
    public ResponseEntity<PageResponse<SongResponse>> searchSongs(
            @RequestParam("query") final String query,
            @RequestParam(defaultValue = "20") final int limit,
            @RequestParam(required = false) final String cursor
    ) {
        final var command = new SearchSongsUseCase.SearchSongsCommand(
                query,
                PageRequest.of(0, limit),
                cursor
        );
        final PageResult<Song> songPage = searchSongsUseCase.searchSongs(command);
        return ResponseEntity.ok(mapper.toPageResponse(songPage));
    }

    @GetMapping("/{songId}")
    public ResponseEntity<SongDetailsResponse> getSongDetails(@PathVariable final UUID songId) {
        final Song song = getSongMetadataUseCase.getSongMetadata(new SongId(songId));
        final URI streamingUrl = getSongStreamUrlUseCase.getSongStreamUrl(song.getId());
        final SongDetailsResponse response = mapper.toResponse(song, streamingUrl);
        return ResponseEntity.ok(response);
    }
}
