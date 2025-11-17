package com.spotpobre.backend.infrastructure.web.mapper;

import com.spotpobre.backend.application.playlist.port.in.CreatePlaylistUseCase.CreatePlaylistCommand;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.UuidMapper;
import com.spotpobre.backend.infrastructure.persistence.kv.model.DynamoDbPage;
import com.spotpobre.backend.infrastructure.web.dto.request.CreatePlaylistRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.PageResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.PlaylistResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PlaylistApiMapper {

    private final UuidMapper uuidMapper;
    private final SongApiMapper songApiMapper;

    public PlaylistApiMapper(UuidMapper uuidMapper, SongApiMapper songApiMapper) {
        this.uuidMapper = uuidMapper;
        this.songApiMapper = songApiMapper;
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
                uuidMapper.playlistIdToUuid(playlist.getId()),
                playlist.getName(),
                uuidMapper.userIdToUuid(playlist.getOwnerId()),
                playlist.getSongs() != null ?
                        playlist.getSongs().stream()
                                .map(songApiMapper::toSongResponse)
                                .collect(Collectors.toList()) :
                        Collections.emptyList()
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

    public PageResponse<PlaylistResponse> toPageResponse(final DynamoDbPage<Playlist> playlistPage) {
        List<PlaylistResponse> content = playlistPage.content().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return new PageResponse<>(
                content,
                playlistPage.lastEvaluatedKey()
        );
    }
}
