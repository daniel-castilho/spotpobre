package com.spotpobre.backend.domain.song.model;

import com.spotpobre.backend.domain.album.model.AlbumId;

import java.util.Objects;

public class Song {

    private SongId id;
    private AlbumId albumId;
    private String title;
    private String storageId;

    private Song(final Builder builder) {
        this.id = builder.id;
        this.albumId = builder.albumId;
        this.title = builder.title;
        this.storageId = builder.storageId;
    }

    public static Song create(final String title, final AlbumId albumId, final String storageId) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Song title cannot be blank.");
        }
        if (albumId == null) {
            throw new IllegalArgumentException("Album ID cannot be null.");
        }
        if (storageId == null || storageId.isBlank()) {
            throw new IllegalArgumentException("Storage ID cannot be blank.");
        }
        return new Builder()
                .id(SongId.generate())
                .albumId(albumId)
                .title(title)
                .storageId(storageId)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public SongId getId() {
        return id;
    }

    public void setId(final SongId id) {
        this.id = id;
    }

    public AlbumId getAlbumId() {
        return albumId;
    }

    public void setAlbumId(final AlbumId albumId) {
        this.albumId = albumId;
    }

    public String getTitle() {
        return title;
    }

    public String getStorageId() {
        return storageId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Song other)) {
            return false;
        }
        return Objects.equals(id, other.id)
                && Objects.equals(albumId, other.albumId)
                && Objects.equals(title, other.title)
                && Objects.equals(storageId, other.storageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, albumId, title, storageId);
    }

    @Override
    public String toString() {
        return "Song{id=" + id
                + ", albumId=" + albumId
                + ", title='" + title + '\''
                + ", storageId='" + storageId + '\''
                + '}';
    }

    public static final class Builder {
        private SongId id;
        private AlbumId albumId;
        private String title;
        private String storageId;

        private Builder() {
        }

        public Builder id(final SongId id) {
            this.id = id;
            return this;
        }

        public Builder albumId(final AlbumId albumId) {
            this.albumId = albumId;
            return this;
        }

        public Builder title(final String title) {
            this.title = title;
            return this;
        }

        public Builder storageId(final String storageId) {
            this.storageId = storageId;
            return this;
        }

        public Song build() {
            return new Song(this);
        }
    }
}
