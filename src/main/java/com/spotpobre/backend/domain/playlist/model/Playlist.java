package com.spotpobre.backend.domain.playlist.model;

import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId; // Import SongId
import com.spotpobre.backend.domain.user.model.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
@Setter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class Playlist {

    private PlaylistId id;
    private String name;
    private UserId ownerId;
    private List<Song> songs;

    public static Playlist create(final String name, final UserId ownerId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Playlist name cannot be blank.");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("Owner ID cannot be null.");
        }
        return new Playlist(PlaylistId.generate(), name, ownerId, new ArrayList<>());
    }

    public void addSong(final Song song) {
        if (songs.size() >= 100) {
            throw new IllegalStateException("Playlist cannot have more than 100 songs.");
        }
        this.songs.add(song);
    }

    public void removeSong(final SongId songId) {
        this.songs.removeIf(song -> song.getId().equals(songId));
    }

    public void updateDetails(final String newName) {
        if (newName != null && !newName.isBlank()) {
            this.name = newName;
        }
    }
}
