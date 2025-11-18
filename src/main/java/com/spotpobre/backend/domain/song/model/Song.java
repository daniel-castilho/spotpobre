package com.spotpobre.backend.domain.song.model;

import com.spotpobre.backend.domain.album.model.AlbumId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class Song {

    private SongId id;
    private AlbumId albumId;
    private String title;
    private String storageId;

    public static Song create(String title, AlbumId albumId, String storageId) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Song title cannot be blank.");
        }
        if (albumId == null) {
            throw new IllegalArgumentException("Album ID cannot be null.");
        }
        if (storageId == null || storageId.isBlank()) {
            throw new IllegalArgumentException("Storage ID cannot be blank.");
        }
        return new Song(SongId.generate(), albumId, title, storageId);
    }
}
