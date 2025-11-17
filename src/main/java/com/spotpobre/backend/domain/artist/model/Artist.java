package com.spotpobre.backend.domain.artist.model;

import com.spotpobre.backend.domain.song.model.Song;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class Artist {

    private ArtistId id;
    private String name;
    private List<Song> songs;

    public static Artist create(final String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Artist name cannot be blank.");
        }
        return new Artist(ArtistId.generate(), name, new ArrayList<>());
    }

    public void addSong(final Song song) {
        this.songs.add(song);
    }
}
