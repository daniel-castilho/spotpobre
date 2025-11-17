package com.spotpobre.backend.domain.playlist.model;

import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.user.model.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter; // Add Setter

import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
@Setter // Add Setter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor // Public no-argument constructor for frameworks
public class Playlist {

    private PlaylistId id;
    private String name;
    private UserId ownerId;
    private List<Song> songs;

    public static Playlist create(final String name, final UserId ownerId) {
        return new Playlist(PlaylistId.generate(), name, ownerId, new ArrayList<Song>());
    }

    public void addSong(final Song song) {
        if (songs.size() >= 100) {
            throw new IllegalStateException("Playlist cannot have more than 100 songs.");
        }
        this.songs.add(song);
    }
}
