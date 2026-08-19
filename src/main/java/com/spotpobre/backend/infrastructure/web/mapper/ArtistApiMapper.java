package com.spotpobre.backend.infrastructure.web.mapper;

import com.spotpobre.backend.application.artist.port.in.CreateArtistUseCase.CreateArtistCommand;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.UuidMapper;
import com.spotpobre.backend.infrastructure.web.dto.request.CreateArtistRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.ArtistResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.PageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {UuidMapper.class, SongApiMapper.class})
public interface ArtistApiMapper {

    CreateArtistCommand toCommand(final CreateArtistRequest request);

    @Mapping(source = "id", target = "id", qualifiedByName = "artistIdToUuid")
    // Removed @Mapping(source = "songs", target = "songs")
    ArtistResponse toResponse(final Artist artist);

    default PageResponse<ArtistResponse> toPageResponse(final PageResult<Artist> page) {
        return new PageResponse<>(
                page.content().stream().map(this::toResponse).collect(Collectors.toList()),
                page.nextPageToken(),
                page.hasNext()
        );
    }
}
