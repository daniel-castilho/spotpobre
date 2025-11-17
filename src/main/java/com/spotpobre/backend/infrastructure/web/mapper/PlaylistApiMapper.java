package com.spotpobre.backend.infrastructure.web.mapper;

import com.spotpobre.backend.application.playlist.port.in.CreatePlaylistUseCase.CreatePlaylistCommand;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.UuidMapper;
import com.spotpobre.backend.infrastructure.web.dto.request.CreatePlaylistRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.PlaylistResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.SongResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PlaylistApiMapper {

    private final UuidMapper uuidMapper;

    public PlaylistApiMapper(UuidMapper uuidMapper) {
        this.uuidMapper = uuidMapper;
    }

    public CreatePlaylistCommand toCommand(final CreatePlaylistRequest request, final UserId ownerId) {
        if (request == null) {
            return null;
        }

        return new CreatePlaylistCommand(
                request.name(),
                ownerId
        );
    }

    public PlaylistResponse toResponse(final Playlist playlist) {
        if (playlist == null) {
            return null;
        }

        return new PlaylistResponse(
                playlist.getId() != null ? playlist.getId().value() : null,
                playlist.getName(),
                playlist.getOwnerId() != null ? UUID.fromString(playlist.getOwnerId().value().toString()) : null,
                playlist.getSongs() != null ? 
                       playlist.getSongs().stream()
                               .map(this::toResponse)
                               .collect(Collectors.toList()) : 
                       List.of()
        );
    }

    public SongResponse toResponse(final Song song) {
        if (song == null) {
            return null;
        }

        return new SongResponse(
                song.getId() != null ? song.getId().value() : null,
                song.getTitle(),
                song.getArtistId() != null ? song.getArtistId().value() : null
        );
    }

    public List<PlaylistResponse> toPlaylistResponseList(List<Playlist> playlists) {
        if (playlists == null) {
            return null;
        }
        return playlists.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<SongResponse> toSongResponseList(List<Song> songs) {
        if (songs == null) {
            return null;
        }
        return songs.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
