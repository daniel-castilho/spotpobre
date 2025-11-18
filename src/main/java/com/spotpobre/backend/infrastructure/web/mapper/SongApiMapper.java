package com.spotpobre.backend.infrastructure.web.mapper;

import com.spotpobre.backend.application.song.port.in.UploadSongUseCase.UploadSongCommand;
import com.spotpobre.backend.domain.album.model.AlbumId; // Import AlbumId
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.UuidMapper;
import com.spotpobre.backend.infrastructure.web.dto.request.UploadSongRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.SongDetailsResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.SongResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.net.URI;
import java.util.UUID;

@Mapper(componentModel = "spring", uses = UuidMapper.class)
public interface SongApiMapper {

    @Mapping(source = "request.title", target = "title")
    @Mapping(source = "request.albumId", target = "albumId", qualifiedByName = "uuidToAlbumId") // Changed
    @Mapping(target = "fileContent", ignore = true)
    @Mapping(target = "contentType", ignore = true)
    UploadSongCommand toCommand(final UploadSongRequest request);

    @Mapping(source = "song.id", target = "id", qualifiedByName = "songIdToUuid")
    @Mapping(source = "song.albumId", target = "albumId", qualifiedByName = "albumIdToUuid") // Changed
    @Mapping(source = "streamingUrl", target = "streamingUrl", qualifiedByName = "uriToString")
    SongDetailsResponse toResponse(final Song song, final URI streamingUrl);

    @Mapping(source = "id", target = "id", qualifiedByName = "songIdToUuid")
    @Mapping(source = "albumId", target = "albumId", qualifiedByName = "albumIdToUuid") // Changed
    SongResponse toSongResponse(final Song song);
}
