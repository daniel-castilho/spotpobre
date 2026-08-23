package com.spotpobre.backend.infrastructure.web.mapper;

import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.UuidMapper; // Corrected: Added import
import com.spotpobre.backend.infrastructure.web.dto.response.AlbumResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.PageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = UuidMapper.class)
public interface AlbumApiMapper {

    @Mapping(source = "id", target = "id", qualifiedByName = "albumIdToUuid")
    @Mapping(source = "artistId", target = "artistId", qualifiedByName = "artistIdToUuid")
    @Mapping(target = "songs", expression = "java(java.util.List.of())")
    AlbumResponse toResponse(Album album);

    default PageResponse<AlbumResponse> toPageResponse(PageResult<Album> page) {
        return new PageResponse<>(
                page.content().stream().map(this::toResponse).collect(Collectors.toList()),
                page.nextPageToken(),
                page.hasNext()
        );
    }
}
