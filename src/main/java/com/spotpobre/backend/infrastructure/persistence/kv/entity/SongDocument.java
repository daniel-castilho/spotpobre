package com.spotpobre.backend.infrastructure.persistence.kv.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.util.Objects;
import java.util.UUID;

@DynamoDbBean
public class SongDocument {
    private String id;
    private String title;
    private UUID artistId;
    private String storageId;

    public SongDocument() {
    }

    private SongDocument(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.artistId = builder.artistId;
        this.storageId = builder.storageId;
    }

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public UUID getArtistId() {
        return artistId;
    }

    public void setArtistId(UUID artistId) {
        this.artistId = artistId;
    }

    public String getStorageId() {
        return storageId;
    }

    public void setStorageId(String storageId) {
        this.storageId = storageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SongDocument that = (SongDocument) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(title, that.title) &&
               Objects.equals(artistId, that.artistId) &&
               Objects.equals(storageId, that.storageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, artistId, storageId);
    }

    @Override
    public String toString() {
        return "SongDocument{" +
               "id='" + id + '\'' +
               ", title='" + title + '\'' +
               ", artistId=" + artistId +
               ", storageId='" + storageId + '\'' +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String title;
        private UUID artistId;
        private String storageId;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder artistId(UUID artistId) {
            this.artistId = artistId;
            return this;
        }

        public Builder storageId(String storageId) {
            this.storageId = storageId;
            return this;
        }

        public SongDocument build() {
            return new SongDocument(this);
        }
    }
}
