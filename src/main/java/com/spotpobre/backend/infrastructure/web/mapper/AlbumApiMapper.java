package com.spotpobre.backend.infrastructure.web.mapper;

import com.spotpobre.backend.application.album.port.in.CreateAlbumUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.UuidMapper; // Corrected: Added import
import com.spotpobre.backend.infrastructure.web.dto.request.CreateAlbumRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.AlbumResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {SongApiMapper.class, UuidMapper.class})
public interface AlbumApiMapper {
    @Mapping(source = "artistId", target = "artistId", qualifiedByName = "uuidToArtistId")
    CreateAlbumUseCase.CreateAlbumCommand toCommand(CreateAlbumRequest request);
    
    @Mapping(source = "id", target = "id", qualifiedByName = "albumIdToUuid")
    @Mapping(source = "artistId", target = "artistId", qualifiedByName = "artistIdToUuid")
    AlbumResponse toResponse(Album album);
}
