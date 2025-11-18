package com.spotpobre.backend.infrastructure.web.mapper;

import com.spotpobre.backend.application.album.port.in.CreateAlbumUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.infrastructure.web.dto.request.CreateAlbumRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.AlbumResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {SongApiMapper.class, UuidMapper.class})
public interface AlbumApiMapper {
    CreateAlbumUseCase.CreateAlbumCommand toCommand(CreateAlbumRequest request);
    AlbumResponse toResponse(Album album);
}
