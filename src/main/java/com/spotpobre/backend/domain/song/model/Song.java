package com.spotpobre.backend.domain.song.model;

import com.spotpobre.backend.domain.artist.model.ArtistId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor // Public no-argument constructor for frameworks
public class Song {

    private SongId id;
    private String title;
    private ArtistId artistId;
    private String storageId;

    public static Song create(final String title, final ArtistId artistId, final String storageId) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Song title cannot be blank.");
        }
        if (artistId == null) {
            throw new IllegalArgumentException("Artist ID cannot be null.");
        }
        if (storageId == null || storageId.isBlank()) {
            throw new IllegalArgumentException("Storage ID cannot be blank.");
        }
        return new Song(SongId.generate(), title, artistId, storageId);
    }
}
