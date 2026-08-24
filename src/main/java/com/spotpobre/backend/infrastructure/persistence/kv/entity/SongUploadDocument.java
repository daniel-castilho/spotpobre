package com.spotpobre.backend.infrastructure.persistence.kv.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.time.Instant;

/**
 * Durable song-upload record (spec §7). The {@code state-expiry-index} GSI (state HASH,
 * expiresAtEpochSeconds RANGE) backs bounded cleanup scans; {@code expiresAtEpochSeconds} is
 * also the DynamoDB TTL attribute. Signed URLs are never stored here.
 */
@DynamoDbBean
public class SongUploadDocument {

    private String songId;
    private String albumId;
    private String artistId;
    private String actorUserId;
    private String contentType;
    private Long contentLengthBytes;
    private String stagingKey;
    private String finalKey;
    private String multipartUploadId;
    private String state;
    private Instant completingLeaseUntil;
    private Instant createdAt;
    private Instant updatedAt;
    private Long expiresAtEpochSeconds;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("songId")
    public String getSongId() {
        return songId;
    }

    public void setSongId(String songId) {
        this.songId = songId;
    }

    @DynamoDbAttribute("albumId")
    public String getAlbumId() {
        return albumId;
    }

    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }

    @DynamoDbAttribute("artistId")
    public String getArtistId() {
        return artistId;
    }

    public void setArtistId(String artistId) {
        this.artistId = artistId;
    }

    @DynamoDbAttribute("actorUserId")
    public String getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(String actorUserId) {
        this.actorUserId = actorUserId;
    }

    @DynamoDbAttribute("contentType")
    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @DynamoDbAttribute("contentLengthBytes")
    public Long getContentLengthBytes() {
        return contentLengthBytes;
    }

    public void setContentLengthBytes(Long contentLengthBytes) {
        this.contentLengthBytes = contentLengthBytes;
    }

    @DynamoDbAttribute("stagingKey")
    public String getStagingKey() {
        return stagingKey;
    }

    public void setStagingKey(String stagingKey) {
        this.stagingKey = stagingKey;
    }

    @DynamoDbAttribute("finalKey")
    public String getFinalKey() {
        return finalKey;
    }

    public void setFinalKey(String finalKey) {
        this.finalKey = finalKey;
    }

    @DynamoDbAttribute("multipartUploadId")
    public String getMultipartUploadId() {
        return multipartUploadId;
    }

    public void setMultipartUploadId(String multipartUploadId) {
        this.multipartUploadId = multipartUploadId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "state-expiry-index")
    @DynamoDbAttribute("state")
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @DynamoDbAttribute("completingLeaseUntil")
    public Instant getCompletingLeaseUntil() {
        return completingLeaseUntil;
    }

    public void setCompletingLeaseUntil(Instant completingLeaseUntil) {
        this.completingLeaseUntil = completingLeaseUntil;
    }

    @DynamoDbAttribute("createdAt")
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("updatedAt")
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @DynamoDbSecondarySortKey(indexNames = "state-expiry-index")
    @DynamoDbAttribute("expiresAtEpochSeconds")
    public Long getExpiresAtEpochSeconds() {
        return expiresAtEpochSeconds;
    }

    public void setExpiresAtEpochSeconds(Long expiresAtEpochSeconds) {
        this.expiresAtEpochSeconds = expiresAtEpochSeconds;
    }
}
