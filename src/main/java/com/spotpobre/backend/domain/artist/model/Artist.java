package com.spotpobre.backend.domain.artist.model;

import com.spotpobre.backend.domain.song.model.Song;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter; // Add Setter

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter // Add Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor // Public no-argument constructor for frameworks
public class Artist {

    private ArtistId id;
    private String name;
    private List<Song> songs;

    public static Artist create(final String name) {
        return new Artist(ArtistId.generate(), name, new ArrayList<Song>());
    }

    public void addSong(final Song song) {
        this.songs.add(song);
    }
}
