package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.song.port.in.GetSongMetadataUseCase;
import com.spotpobre.backend.application.song.port.in.GetSongStreamUrlUseCase;
import com.spotpobre.backend.application.song.port.in.SearchSongsUseCase;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.infrastructure.web.dto.response.PageResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.SongDetailsResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.SongResponse;
import com.spotpobre.backend.infrastructure.web.mapper.SongApiMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<Page<SongResponse>> searchSongs(
            @RequestParam("query") final String query,
            final Pageable pageable
    ) {
        final var command = new SearchSongsUseCase.SearchSongsCommand(query, pageable);
        final Page<Song> songPage = searchSongsUseCase.searchSongs(command);
        return ResponseEntity.ok(songPage.map(mapper::toSongResponse));
    }

    @GetMapping("/{songId}")
    public ResponseEntity<SongDetailsResponse> getSongDetails(@PathVariable final UUID songId) {
        final Song song = getSongMetadataUseCase.getSongMetadata(new SongId(songId));
        final URI streamingUrl = getSongStreamUrlUseCase.getSongStreamUrl(song.getId());
        final SongDetailsResponse response = mapper.toResponse(song, streamingUrl);
        return ResponseEntity.ok(response);
    }
}
