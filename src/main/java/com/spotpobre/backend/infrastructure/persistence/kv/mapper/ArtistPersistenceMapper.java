package com.spotpobre.backend.infrastructure.persistence.kv.mapper;

import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = UuidMapper.class)
public interface ArtistPersistenceMapper {

    @Mapping(source = "id", target = "id", qualifiedByName = "artistIdToUuid")
    ArtistDocument toDocument(final Artist artist);

    @Mapping(source = "id", target = "id", qualifiedByName = "stringToArtistId")
    Artist toDomain(final ArtistDocument document);

    List<ArtistDocument> toDocumentList(List<Artist> artists);
    List<Artist> toDomainList(List<ArtistDocument> documents);
}
