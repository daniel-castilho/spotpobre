package com.spotpobre.backend.domain.album.model;

import com.spotpobre.backend.domain.artist.model.ArtistId;

import java.util.Objects;

public class Album {

    private final AlbumId id;
    private ArtistId artistId;
    private String name;
    private String coverArtUrl;

    private Album(final Builder builder) {
        this.id = builder.id;
        this.artistId = builder.artistId;
        this.name = builder.name;
        this.coverArtUrl = builder.coverArtUrl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public AlbumId getId() {
        return id;
    }

    public ArtistId getArtistId() {
        return artistId;
    }

    public void setArtistId(final ArtistId artistId) {
        this.artistId = artistId;
    }

    public String getName() {
        return name;
    }

    public String getCoverArtUrl() {
        return coverArtUrl;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Album other)) {
            return false;
        }
        return Objects.equals(id, other.id)
                && Objects.equals(artistId, other.artistId)
                && Objects.equals(name, other.name)
                && Objects.equals(coverArtUrl, other.coverArtUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, artistId, name, coverArtUrl);
    }

    @Override
    public String toString() {
        return "Album{id=" + id
                + ", artistId=" + artistId
                + ", name='" + name + '\''
                + ", coverArtUrl='" + coverArtUrl + '\''
                + '}';
    }

    public static final class Builder {
        private AlbumId id;
        private ArtistId artistId;
        private String name;
        private String coverArtUrl;

        private Builder() {
        }

        public Builder id(final AlbumId id) {
            this.id = id;
            return this;
        }

        public Builder artistId(final ArtistId artistId) {
            this.artistId = artistId;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder coverArtUrl(final String coverArtUrl) {
            this.coverArtUrl = coverArtUrl;
            return this;
        }

        public Album build() {
            return new Album(this);
        }
    }
}
