package com.spotpobre.backend.domain.playlist.model;

import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.user.model.UserId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Playlist {

    public static final int MAX_SONGS = 100;

    private PlaylistId id;
    private String name;
    private UserId ownerId;
    private List<Song> songs;
    private long version;

    private Playlist(final Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.ownerId = builder.ownerId;
        this.songs = builder.songs != null ? builder.songs : new ArrayList<>();
        this.version = builder.version;
    }

    public static Playlist create(final String name, final UserId ownerId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Playlist name cannot be blank.");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("Owner ID cannot be null.");
        }
        return new Builder()
                .id(PlaylistId.generate())
                .name(name)
                .ownerId(ownerId)
                .songs(new ArrayList<>())
                .version(0L)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public PlaylistId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UserId getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(final UserId ownerId) {
        this.ownerId = ownerId;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(final long version) {
        this.version = version;
    }

    public void addSong(final Song song) {
        if (songs.size() >= MAX_SONGS) {
            throw new IllegalStateException("Playlist cannot have more than " + MAX_SONGS + " songs.");
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Playlist other)) {
            return false;
        }
        return Objects.equals(id, other.id)
                && Objects.equals(name, other.name)
                && Objects.equals(ownerId, other.ownerId)
                && Objects.equals(songs, other.songs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, ownerId, songs);
    }

    @Override
    public String toString() {
        return "Playlist{id=" + id
                + ", name='" + name + '\''
                + ", ownerId=" + ownerId
                + ", songs=" + songs
                + '}';
    }

    public static final class Builder {
        private PlaylistId id;
        private String name;
        private UserId ownerId;
        private List<Song> songs;
        private long version;

        private Builder() {
        }

        public Builder id(final PlaylistId id) {
            this.id = id;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder ownerId(final UserId ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        public Builder songs(final List<Song> songs) {
            this.songs = songs;
            return this;
        }

        public Builder version(final long version) {
            this.version = version;
            return this;
        }

        public Playlist build() {
            return new Playlist(this);
        }
    }
}
