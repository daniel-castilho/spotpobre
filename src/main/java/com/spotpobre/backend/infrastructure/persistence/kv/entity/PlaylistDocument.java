package com.spotpobre.backend.infrastructure.persistence.kv.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@DynamoDbBean
public class PlaylistDocument {

    private String id;
    private String name;
    private UUID ownerId;
    private List<SongDocument> songs;
    private Long version;

    public PlaylistDocument() {
    }

    private PlaylistDocument(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.ownerId = builder.ownerId;
        this.songs = builder.songs;
        this.version = builder.version;
    }

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public List<SongDocument> getSongs() {
        return songs;
    }

    public void setSongs(List<SongDocument> songs) {
        this.songs = songs;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaylistDocument that = (PlaylistDocument) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(name, that.name) &&
               Objects.equals(ownerId, that.ownerId) &&
               Objects.equals(songs, that.songs) &&
               Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, ownerId, songs, version);
    }

    @Override
    public String toString() {
        return "PlaylistDocument{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", ownerId=" + ownerId +
               ", songs=" + songs +
               ", version=" + version +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String name;
        private UUID ownerId;
        private List<SongDocument> songs;
        private Long version;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder ownerId(UUID ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        public Builder songs(List<SongDocument> songs) {
            this.songs = songs;
            return this;
        }

        public Builder version(Long version) {
            this.version = version;
            return this;
        }

        public PlaylistDocument build() {
            return new PlaylistDocument(this);
        }
    }
}
