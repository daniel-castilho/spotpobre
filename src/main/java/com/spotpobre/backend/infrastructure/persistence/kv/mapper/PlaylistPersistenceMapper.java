package com.spotpobre.backend.infrastructure.persistence.kv.mapper;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PlaylistPersistenceMapper {

    private final SongPersistenceMapper songPersistenceMapper;

    public PlaylistPersistenceMapper(SongPersistenceMapper songPersistenceMapper) {
        this.songPersistenceMapper = songPersistenceMapper;
    }

    public PlaylistDocument toDocument(final Playlist playlist) {
        if (playlist == null) {
            return null;
        }

        return PlaylistDocument.builder()
                .id(playlist.getId() != null ? playlist.getId().value().toString() : null)
                .name(playlist.getName())
                .ownerId(playlist.getOwnerId() != null ? UUID.fromString(playlist.getOwnerId().value().toString()) : null)
                .songs(playlist.getSongs() != null ?
                        songPersistenceMapper.toDocumentList(playlist.getSongs()) :
                        Collections.emptyList())
                .build();
    }

    public Playlist toDomain(final PlaylistDocument document) {
        if (document == null) {
            return null;
        }

        return Playlist.builder()
                .id(document.getId() != null ? new PlaylistId(UUID.fromString(document.getId())) : null)
                .name(document.getName())
                .ownerId(document.getOwnerId() != null ?
                        UserId.from(document.getOwnerId().toString()) :
                        null)
                .songs(document.getSongs() != null ?
                        songPersistenceMapper.toDomainList(document.getSongs()) :
                        new ArrayList<>())
                .build();
    }

    public List<PlaylistDocument> toDocumentList(List<Playlist> playlists) {
        if (playlists == null) {
            return null;
        }
        return playlists.stream()
                .map(this::toDocument)
                .collect(Collectors.toList());
    }

    public List<Playlist> toDomainList(List<PlaylistDocument> documents) {
        if (documents == null) {
            return null;
        }
        return documents.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }
}
