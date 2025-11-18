package com.spotpobre.backend.infrastructure.persistence.kv.mapper;

import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.AlbumDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {SongPersistenceMapper.class, UuidMapper.class})
public interface AlbumPersistenceMapper {

    @Mapping(source = "id", target = "id", qualifiedByName = "stringToAlbumId")
    @Mapping(source = "artistId", target = "artistId", qualifiedByName = "uuidToArtistId")
    Album toDomain(AlbumDocument document);

    @Mapping(source = "id", target = "id", qualifiedByName = "albumIdToUuid")
    @Mapping(source = "artistId", target = "artistId", qualifiedByName = "artistIdToUuid")
    AlbumDocument toDocument(Album domain);
}
