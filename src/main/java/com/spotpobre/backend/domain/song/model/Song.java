package com.spotpobre.backend.domain.song.model;

import com.spotpobre.backend.domain.artist.model.ArtistId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter; // Add Setter

@Getter
@Setter // Add Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor // Public no-argument constructor for frameworks
public class Song {

    private SongId id;
    private String title;
    private ArtistId artistId;
    private String storageId;

    public static Song create(final String title, final ArtistId artistId, final String storageId) {
        return new Song(SongId.generate(), title, artistId, storageId);
    }
}
