package com.spotpobre.backend.infrastructure.persistence.kv.mapper;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", uses = {SongPersistenceMapper.class, UuidMapper.class})
public interface PlaylistPersistenceMapper {

    @Mapping(source = "id", target = "id", qualifiedByName = "playlistIdToUuid")
    @Mapping(source = "ownerId", target = "ownerId", qualifiedByName = "userIdToUuid")
    @Mapping(source = "songs", target = "songs")
    PlaylistDocument toDocument(final Playlist playlist);

    @Mapping(source = "id", target = "id", qualifiedByName = "stringToPlaylistId")
    @Mapping(source = "ownerId", target = "ownerId", qualifiedByName = "stringToUserId")
    @Mapping(source = "songs", target = "songs")
    Playlist toDomain(final PlaylistDocument document);

    List<PlaylistDocument> toDocumentList(List<Playlist> playlists);
    List<Playlist> toDomainList(List<PlaylistDocument> documents);
}
