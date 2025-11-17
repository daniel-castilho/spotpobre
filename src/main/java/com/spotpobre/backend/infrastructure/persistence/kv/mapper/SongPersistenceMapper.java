package com.spotpobre.backend.infrastructure.persistence.kv.mapper;

import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.infrastructure.persistence.kv.entity.SongDocument;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SongPersistenceMapper {

    private final UuidMapper uuidMapper;

    public SongPersistenceMapper(UuidMapper uuidMapper) {
        this.uuidMapper = uuidMapper;
    }

    public SongDocument toDocument(final Song song) {
        if (song == null) {
            return null;
        }

        return SongDocument.builder()
                .id(song.getId() != null ? song.getId().value().toString() : null)
                .title(song.getTitle())
                .artistId(song.getArtistId() != null ? song.getArtistId().value() : null)
                .storageId(song.getStorageId())
                .build();
    }

    public Song toDomain(final SongDocument document) {
        if (document == null) {
            return null;
        }

        return Song.builder()
                .id(document.getId() != null ? SongId.from(document.getId()) : null)
                .title(document.getTitle())
                .artistId(document.getArtistId() != null ?
                        new com.spotpobre.backend.domain.artist.model.ArtistId(document.getArtistId()) :
                        null)
                .storageId(document.getStorageId())
                .build();
    }

    public List<SongDocument> toDocumentList(List<Song> songs) {
        if (songs == null) {
            return null;
        }
        return songs.stream()
                .map(this::toDocument)
                .collect(Collectors.toList());
    }

    public List<Song> toDomainList(List<SongDocument> documents) {
        if (documents == null) {
            return null;
        }
        return documents.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }
}
