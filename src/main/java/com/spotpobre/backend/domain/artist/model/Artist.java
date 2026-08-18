package com.spotpobre.backend.domain.artist.model;

import java.util.Objects;

public class Artist {

    private ArtistId id;
    private String name;

    private Artist(final Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
    }

    public static Artist create(final String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Artist name cannot be blank.");
        }
        return new Builder()
                .id(ArtistId.generate())
                .name(name)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public ArtistId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Artist other)) {
            return false;
        }
        return Objects.equals(id, other.id) && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "Artist{id=" + id + ", name='" + name + "'}";
    }

    public static final class Builder {
        private ArtistId id;
        private String name;

        private Builder() {
        }

        public Builder id(final ArtistId id) {
            this.id = id;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Artist build() {
            return new Artist(this);
        }
    }
}
