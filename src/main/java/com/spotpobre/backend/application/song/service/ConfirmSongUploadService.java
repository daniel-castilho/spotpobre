package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.artist.port.in.RequireArtistAccessUseCase;
import com.spotpobre.backend.application.song.port.in.ConfirmSongUploadUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.common.ConflictException;
import com.spotpobre.backend.domain.common.IdempotencyInProgressException;
import com.spotpobre.backend.domain.common.IdempotencyLeaseLostException;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.common.UploadIntegrityException;
import com.spotpobre.backend.domain.song.model.CompletedUploadPart;
import com.spotpobre.backend.domain.song.model.ConfirmUploadCommand;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongUpload;
import com.spotpobre.backend.domain.song.model.SongUploadState;
import com.spotpobre.backend.domain.song.model.StorageObjectHead;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import com.spotpobre.backend.domain.song.port.SongUploadRepository;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Server-authoritative upload confirmation (spec §7): keys and multipart ids come from the
 * durable {@link SongUpload} record, never from client input. The confirmation acquires an
 * exclusive COMPLETING lease, verifies declared size/type against the stored object, promotes
 * the staging object to its final playable key, and transactionally creates the Song row while
 * marking the upload COMPLETED. Completed replays return the same Song (200).
 */
@RequiredArgsConstructor
public class ConfirmSongUploadService implements ConfirmSongUploadUseCase {

    static final Duration COMPLETION_LEASE = Duration.ofSeconds(120);

    private final SongStoragePort songStoragePort;
    private final SongMetadataRepository songMetadataRepository;
    private final SongUploadRepository songUploadRepository;
    private final AlbumRepository albumRepository;
    private final RequireArtistAccessUseCase requireArtistAccess;
    private final Clock clock;

    @Override
    public Song confirmUpload(final ConfirmSongUploadCommand command) {
        final SongUpload upload = songUploadRepository.findBySongId(command.songId())
                .orElseThrow(() -> new NotFoundException("No staged upload for song: " + command.songId()));

        if (!upload.getAlbumId().equals(command.albumId())) {
            throw new NotFoundException("Song does not belong to album: " + command.albumId());
        }
        final Album album = albumRepository.findById(upload.getAlbumId())
                .orElseThrow(() -> new NotFoundException("Album not found: " + upload.getAlbumId()));
        requireArtistAccess.requireAccess(
                new RequireArtistAccessUseCase.ActorArtistRef(command.actorUserId(), command.actorIsAdmin()),
                album.getArtistId());

        // Client-supplied coordinates must match the server-side record exactly.
        if (!upload.getStagingKey().equals(command.storageKey())) {
            throw new ConflictException("Storage key does not match the staged upload record.");
        }
        if (command.multipartUploadId() != null && !command.multipartUploadId().isBlank()
                && !command.multipartUploadId().equals(upload.getMultipartUploadId())) {
            throw new ConflictException("Multipart upload id does not match the staged upload record.");
        }

        // Completed replay: same Song, 200 — never a second completion.
        if (upload.getState() == SongUploadState.COMPLETED) {
            return songMetadataRepository.findById(upload.getSongId())
                    .orElseGet(() -> {
                        // Crash-window repair: upload completed but the Song row is missing.
                        final Song repaired = Song.create(upload.getSongId(), upload.getTitle(),
                                upload.getAlbumId(), upload.getFinalKey());
                        songMetadataRepository.save(repaired);
                        return repaired;
                    });
        }

        if (upload.getState() != SongUploadState.PENDING_UPLOAD) {
            throw new ConflictException("This upload is no longer confirmable (state: "
                    + upload.getState() + ").");
        }

        // Exclusive lease: concurrent confirmations elect exactly one completer.
        validateCompletedParts(command.completedParts(), upload);
        final Instant now = clock.instant();
        final Instant leaseUntil = now.plus(COMPLETION_LEASE);
        if (!songUploadRepository.acquireCompletingLease(upload.getSongId(), leaseUntil, now)) {
            final var observed = songUploadRepository.findBySongId(upload.getSongId()).orElseThrow();
            long retryAfter = observed.getCompletingLeaseUntil() == null ? 2
                    : Math.max(1, Duration.between(now, observed.getCompletingLeaseUntil()).getSeconds());
            throw new IdempotencyInProgressException(
                    "Another confirmation for this upload is already in progress.", retryAfter);
        }
        // Mirror the acquired lease into the entity so the completion transaction's CAS guard
        // matches exactly what this caller holds.
        upload.markCompleting(leaseUntil);

        try {
            // Complete/recover the S3 side first (multipart needs the client part ETags).
            songStoragePort.confirmUpload(new ConfirmUploadCommand(
                    upload.getStagingKey(), upload.getMultipartUploadId(), command.completedParts()));

            // Integrity gate: actual bytes vs declared contract.
            verifyIntegrity(upload);

            // Promote staging -> final; then create Song + mark COMPLETED atomically.
            songStoragePort.promoteObject(upload.getStagingKey(), upload.getFinalKey());

            final Song song = Song.create(upload.getSongId(), upload.getTitle(),
                    upload.getAlbumId(), upload.getFinalKey());
            if (!songUploadRepository.markCompletedAndCreateSongIfAbsent(upload, song,
                    clock.instant())) {
                throw new IdempotencyLeaseLostException(
                        "The completing lease was lost before the result could be recorded; retry.");
            }
            return song;
        } catch (UploadIntegrityException e) {
            quarantine(upload);
            throw e;
        }
    }

    /**
     * Client-supplied multipart evidence must be structurally sane before any storage or
     * lease work (spec S15): positive unique part numbers in ascending order, non-blank ETags.
     */
    private void validateCompletedParts(final List<CompletedUploadPart> parts,
                                        final SongUpload upload) {
        if (!upload.isMultipart()) {
            return; // Single-part confirmations carry no part list.
        }
        if (parts == null || parts.isEmpty()) {
            throw new ConflictException("Multipart confirmation requires completed parts.");
        }
        int previous = 0;
        for (CompletedUploadPart part : parts) {
            if (part == null || part.partNumber() < 1) {
                throw new ConflictException("Completed parts must use part numbers starting at 1.");
            }
            if (part.partNumber() <= previous) {
                throw new ConflictException(
                        "Completed parts must have unique part numbers in ascending order.");
            }
            if (part.eTag() == null || part.eTag().isBlank()) {
                throw new ConflictException("Every completed part requires its ETag.");
            }
            previous = part.partNumber();
        }
    }

    private void verifyIntegrity(final SongUpload upload) {
        final StorageObjectHead head = songStoragePort.headObject(upload.getStagingKey());
        if (head.contentLengthBytes() != upload.getContentLengthBytes()) {
            throw new UploadIntegrityException("Uploaded size mismatch: expected "
                    + upload.getContentLengthBytes() + " bytes, found " + head.contentLengthBytes());
        }
        if (!head.contentType().equalsIgnoreCase(upload.getContentType())) {
            throw new UploadIntegrityException("Uploaded content type mismatch: expected "
                    + upload.getContentType() + ", found " + head.contentType());
        }
    }

    /** Quarantine path for integrity failures: remove staging bytes, abort remnants, abort state. */
    private void quarantine(final SongUpload upload) {
        if (upload.getMultipartUploadId() != null) {
            songStoragePort.abortUpload(upload.getStagingKey(), upload.getMultipartUploadId());
        }
        songStoragePort.deleteObject(upload.getStagingKey());
        // Release our own live lease first: the terminal abort only accepts pending records or
        // EXPIRED completing leases, so a crash here still converges via cleanup reconciliation.
        songUploadRepository.releaseCompletingLease(upload.getSongId(),
                upload.getCompletingLeaseUntil(), clock.instant());
        songUploadRepository.markAbortedFromPendingOrExpiredCompleting(upload.getSongId(),
                clock.instant());
    }
}
