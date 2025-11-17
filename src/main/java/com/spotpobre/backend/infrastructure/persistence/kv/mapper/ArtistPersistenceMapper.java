package com.spotpobre.backend.infrastructure.persistence.kv.mapper;

import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.ArtistDocument;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ArtistPersistenceMapper {

    private final SongPersistenceMapper songPersistenceMapper;

    @Autowired
    public ArtistPersistenceMapper(SongPersistenceMapper songPersistenceMapper) {
        this.songPersistenceMapper = songPersistenceMapper;
    }

    public ArtistDocument toDocument(final Artist artist) {
        if (artist == null) {
            return null;
        }

        return ArtistDocument.builder()
                .id(artist.getId() != null ? artist.getId().value().toString() : null)
                .name(artist.getName())
                .songs(artist.getSongs() != null ? 
                       mapSongsToDocument(artist.getSongs()) : 
                       null)
                .build();
    }

    public Artist toDomain(final ArtistDocument document) {
        if (document == null) {
            return null;
        }

        return Artist.builder()
                .id(ArtistId.from(document.getId()))
                .name(document.getName())
                .songs(document.getSongs() != null ? 
                       mapDocumentsToSongs(document.getSongs()) : 
                       null)
                .build();
    }

    private List<SongDocument> mapSongsToDocument(List<Song> songs) {
        return songs.stream()
                .map(songPersistenceMapper::toDocument)
                .collect(Collectors.toList());
    }

    private List<Song> mapDocumentsToSongs(List<SongDocument> documents) {
        return documents.stream()
                .map(songPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }
}
