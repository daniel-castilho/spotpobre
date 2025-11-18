package com.spotpobre.backend.infrastructure.persistence.kv.mapper;

import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.AlbumDocument;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {SongPersistenceMapper.class, UuidMapper.class})
public interface AlbumPersistenceMapper {
    Album toDomain(AlbumDocument document);
    AlbumDocument toDocument(Album domain);
}
