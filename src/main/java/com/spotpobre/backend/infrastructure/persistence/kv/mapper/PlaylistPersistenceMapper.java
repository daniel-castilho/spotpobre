package com.spotpobre.backend.infrastructure.persistence.kv.mapper;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

        PlaylistDocument document = new PlaylistDocument();
        document.setId(playlist.getId() != null ? playlist.getId().value().toString() : null);
        document.setName(playlist.getName());
        document.setOwnerId(playlist.getOwnerId() != null ? playlist.getOwnerId().value() : null); // Use value directly
        document.setSongs(playlist.getSongs() != null ?
                songPersistenceMapper.toDocumentList(playlist.getSongs()) :
                Collections.emptyList());
        return document;
    }

    public Playlist toDomain(final PlaylistDocument document) {
        if (document == null) {
            return null;
        }

        Playlist playlist = new Playlist();
        playlist.setId(document.getId() != null ? new PlaylistId(UUID.fromString(document.getId())) : null);
        playlist.setName(document.getName());
        playlist.setOwnerId(document.getOwnerId() != null ? new UserId(document.getOwnerId()) : null); // Use UserId constructor
        playlist.setSongs(document.getSongs() != null ?
                songPersistenceMapper.toDomainList(document.getSongs()) :
                new ArrayList<>());
        return playlist;
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

    public Page<Playlist> toDomainPage(final Page<PlaylistDocument> documentPage) {
        List<Playlist> domainPlaylists = documentPage.getContent().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
        return new PageImpl<>(domainPlaylists, documentPage.getPageable(), documentPage.getTotalElements());
    }
}
