package com.spotpobre.backend.infrastructure.persistence.kv.mapper;

import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = UuidMapper.class)
public interface SongPersistenceMapper {

    @Mapping(source = "id", target = "id", qualifiedByName = "songIdToUuid")
    @Mapping(source = "albumId", target = "albumId", qualifiedByName = "albumIdToUuid")
    SongDocument toDocument(final Song song);

    @Mapping(source = "id", target = "id", qualifiedByName = "stringToSongId")
    @Mapping(source = "albumId", target = "albumId", qualifiedByName = "stringToAlbumId")
    Song toDomain(final SongDocument document);

    List<SongDocument> toDocumentList(List<Song> songs);
    List<Song> toDomainList(List<SongDocument> documents);
}
