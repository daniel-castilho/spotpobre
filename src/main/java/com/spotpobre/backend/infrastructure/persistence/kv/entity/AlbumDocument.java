package com.spotpobre.backend.infrastructure.persistence.kv.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.util.UUID;

@DynamoDbBean
public class AlbumDocument {
    private String id;
    private String name;
    private UUID artistId;
    private String coverArtUrl;

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @DynamoDbSecondaryPartitionKey(indexNames = "artistId-index")
    public UUID getArtistId() { return artistId; }
    public void setArtistId(UUID artistId) { this.artistId = artistId; }

    public String getCoverArtUrl() { return coverArtUrl; }
    public void setCoverArtUrl(String coverArtUrl) { this.coverArtUrl = coverArtUrl; }
}