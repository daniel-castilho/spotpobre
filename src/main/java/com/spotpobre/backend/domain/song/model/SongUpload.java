package com.spotpobre.backend.domain.song.model;

import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.artist.model.ArtistId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable record of one song upload attempt (spec §7): the authoritative source for
 * initiation, confirmation and cleanup. Pure Java — no framework or storage types.
 *
 * <p>The upload reserves the song identity before any bytes exist. Only a {@link Song}
 * created at confirmation time becomes visible (fetch/search/stream/like); pending uploads are
 * invisible by construction because no Songs-table row is written until promotion.</p>
 *
 * <p>State transitions are validated here: illegal ones throw {@link IllegalStateException}.
 * The {@code COMPLETING} state carries an exclusive lease so concurrent confirmations cannot
 * double-promote; stale holders are rejected by conditional repository writes.</p>
 */
public final class SongUpload {

    private final SongId songId;
    private final String title;
    private final AlbumId albumId;
    private final ArtistId artistId;
    private final UUID actorUserId;
    private final String contentType;
    private final long contentLengthBytes;
    private final String stagingKey;
    private final String finalKey;
    private String multipartUploadId;
    private SongUploadState state;
    private Instant completingLeaseUntil;
    private final Instant createdAt;
    private Instant updatedAt;
    private long expiresAtEpochSeconds;

    private SongUpload(final SongId songId, final String title, final AlbumId albumId,
                       final ArtistId artistId,
                       final UUID actorUserId, final String contentType, final long contentLengthBytes,
                       final String stagingKey, final String finalKey, final String multipartUploadId,
                       final SongUploadState state, final Instant completingLeaseUntil,
                       final Instant createdAt, final Instant updatedAt, final long expiresAtEpochSeconds) {
        this.songId = Objects.requireNonNull(songId, "songId is required");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        this.title = title;
        this.albumId = Objects.requireNonNull(albumId, "albumId is required");
        this.artistId = Objects.requireNonNull(artistId, "artistId is required");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId is required");
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType is required");
        }
        if (contentLengthBytes <= 0) {
            throw new IllegalArgumentException("contentLengthBytes must be positive");
        }
        this.contentType = contentType;
        this.contentLengthBytes = contentLengthBytes;
        if (stagingKey == null || stagingKey.isBlank() || finalKey == null || finalKey.isBlank()) {
            throw new IllegalArgumentException("stagingKey and finalKey are required");
        }
        this.stagingKey = stagingKey;
        this.finalKey = finalKey;
        this.multipartUploadId = emptyToNull(multipartUploadId);
        this.state = state == null ? SongUploadState.PENDING_UPLOAD : state;
        this.completingLeaseUntil = completingLeaseUntil;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        this.expiresAtEpochSeconds = expiresAtEpochSeconds;
    }

    /**
     * Creates a new PENDING_UPLOAD record reserving {@code songId}. Staging/final keys are
     * derived server-side — never accepted from client input.
     */
    public static SongUpload start(final SongId songId, final String title, final AlbumId albumId,
                                   final ArtistId artistId,
                                   final UUID actorUserId, final String contentType,
                                   final long contentLengthBytes, final String multipartUploadId,
                                   final Instant now, final Instant logicalExpiry) {
        return new SongUpload(songId, title, albumId, artistId, actorUserId, contentType,
                contentLengthBytes,
                stagingKeyFor(songId), finalKeyFor(songId), multipartUploadId,
                SongUploadState.PENDING_UPLOAD, null, now, now,
                logicalExpiry == null ? 0L : logicalExpiry.getEpochSecond());
    }

    /** Rehydrates a persisted record without re-deriving keys or re-validating transitions. */
    public static SongUpload rehydrate(final SongId songId, final String title, final AlbumId albumId,
                                       final ArtistId artistId,
                                       final UUID actorUserId, final String contentType,
                                       final long contentLengthBytes, final String stagingKey,
                                       final String finalKey, final String multipartUploadId,
                                       final SongUploadState state, final Instant completingLeaseUntil,
                                       final Instant createdAt, final Instant updatedAt,
                                       final long expiresAtEpochSeconds) {
        return new SongUpload(songId, title, albumId, artistId, actorUserId, contentType,
                contentLengthBytes, stagingKey, finalKey, multipartUploadId, state,
                completingLeaseUntil, createdAt, updatedAt, expiresAtEpochSeconds);
    }

    /** Binds (or rebinds) the S3 multipart id once logically; recovery reuses the same id. */
    public void attachMultipartUploadId(final String multipartUploadId) {
        if (state != SongUploadState.PENDING_UPLOAD) {
            throw new IllegalStateException(
                    "Multipart id can only be attached while PENDING_UPLOAD (state: " + state + ")");
        }
        if (this.multipartUploadId != null && !this.multipartUploadId.isBlank()
                && !this.multipartUploadId.equals(multipartUploadId)) {
            throw new IllegalStateException("A different multipart upload id is already bound");
        }
        this.multipartUploadId = emptyToNull(multipartUploadId);
    }

    public void markCompleting(final Instant leaseUntil) {
        transitionTo(SongUploadState.COMPLETING);
        this.completingLeaseUntil = Objects.requireNonNull(leaseUntil, "leaseUntil is required");
    }

    public void markReleasedFromCompleting() {
        transitionTo(SongUploadState.PENDING_UPLOAD);
        this.completingLeaseUntil = null;
    }

    public void markCompleted() {
        transitionTo(SongUploadState.COMPLETED);
        this.completingLeaseUntil = null;
    }

    public void markAborted() {
        transitionTo(SongUploadState.ABORTED);
        this.completingLeaseUntil = null;
    }

    public boolean completingLeaseActiveAt(final Instant now) {
        return state == SongUploadState.COMPLETING
                && completingLeaseUntil != null
                && now.isBefore(completingLeaseUntil);
    }

    private void transitionTo(final SongUploadState target) {
        if (!state.canTransitionTo(target)) {
            throw new IllegalStateException("Illegal upload transition " + state + " -> " + target);
        }
        this.state = target;
    }

    private static String emptyToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public static String stagingKeyFor(final SongId songId) {
        return "pending/" + songId.value();
    }

    public static String finalKeyFor(final SongId songId) {
        return "songs/" + songId.value();
    }

    public SongId getSongId() {
        return songId;
    }

    public String getTitle() {
        return title;
    }

    public AlbumId getAlbumId() {
        return albumId;
    }

    public ArtistId getArtistId() {
        return artistId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getContentType() {
        return contentType;
    }

    public long getContentLengthBytes() {
        return contentLengthBytes;
    }

    public String getStagingKey() {
        return stagingKey;
    }

    public String getFinalKey() {
        return finalKey;
    }

    public String getMultipartUploadId() {
        return multipartUploadId;
    }

    public SongUploadState getState() {
        return state;
    }

    public Instant getCompletingLeaseUntil() {
        return completingLeaseUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public long getExpiresAtEpochSeconds() {
        return expiresAtEpochSeconds;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SongUpload other)) {
            return false;
        }
        return songId.equals(other.songId)
                && title.equals(other.title)
                && albumId.equals(other.albumId)
                && artistId.equals(other.artistId)
                && actorUserId.equals(other.actorUserId)
                && contentType.equals(other.contentType)
                && contentLengthBytes == other.contentLengthBytes
                && stagingKey.equals(other.stagingKey)
                && finalKey.equals(other.finalKey)
                && Objects.equals(multipartUploadId, other.multipartUploadId)
                && state == other.state
                && Objects.equals(completingLeaseUntil, other.completingLeaseUntil)
                && createdAt.equals(other.createdAt)
                && updatedAt.equals(other.updatedAt)
                && expiresAtEpochSeconds == other.expiresAtEpochSeconds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(songId, title, albumId, artistId, actorUserId, contentType, contentLengthBytes,
                stagingKey, finalKey, multipartUploadId, state, completingLeaseUntil,
                createdAt, updatedAt, expiresAtEpochSeconds);
    }

    @Override
    public String toString() {
        return "SongUpload{" + songId.value() + ", title=" + title
                + ", state=" + state
                + ", album=" + albumId.value() + ", artist=" + artistId.value()
                + ", multipart=" + (multipartUploadId != null ? "bound" : "none") + "}";
    }
}
