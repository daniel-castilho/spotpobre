package com.spotpobre.backend.infrastructure.web.mapper;

import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.UuidMapper;
import com.spotpobre.backend.infrastructure.web.dto.request.CreateArtistRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.ArtistResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UuidMapper.class, SongApiMapper.class})
public interface ArtistApiMapper {

    Artist toDomain(final CreateArtistRequest request);

    @Mapping(source = "id", target = "id", qualifiedByName = "artistIdToUuid")
    ArtistResponse toResponse(final Artist artist);
}
