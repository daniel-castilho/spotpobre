package com.spotpobre.backend.infrastructure.web.mapper;

import com.spotpobre.backend.application.song.port.in.UploadSongUseCase.UploadSongCommand;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.UuidMapper;
import com.spotpobre.backend.infrastructure.web.dto.request.UploadSongRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.SongDetailsResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.net.URI;
import java.util.UUID;

@Mapper(componentModel = "spring", uses = UuidMapper.class)
public interface SongApiMapper {

    @Mapping(source = "request.title", target = "title")
    @Mapping(source = "request.artistId", target = "artistId", qualifiedByName = "uuidToArtistId") // Corrected mapping
    @Mapping(target = "fileContent", ignore = true) // Handled separately in controller
    @Mapping(target = "contentType", ignore = true) // Handled separately in controller
    UploadSongCommand toCommand(final UploadSongRequest request);

    @Mapping(source = "song.id", target = "id", qualifiedByName = "songIdToUuid")
    @Mapping(source = "song.artistId", target = "artistId", qualifiedByName = "artistIdToUuid")
    @Mapping(source = "streamingUrl", target = "streamingUrl")
    SongDetailsResponse toResponse(final Song song, final URI streamingUrl);

    // Helper method for ArtistId conversion if needed in command
    @Named("uuidToArtistId")
    default ArtistId uuidToArtistId(UUID uuid) {
        return uuid != null ? new ArtistId(uuid) : null;
    }
}
