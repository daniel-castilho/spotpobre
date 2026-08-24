package com.spotpobre.backend.infrastructure.storage.s3;

import com.spotpobre.backend.domain.song.model.CompletedUploadPart;
import com.spotpobre.backend.domain.song.model.ConfirmUploadCommand;
import com.spotpobre.backend.domain.song.model.PresignedUploadPart;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.SongUploadCommand;
import com.spotpobre.backend.domain.song.model.StorageObjectHead;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import com.spotpobre.backend.infrastructure.config.properties.AwsProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3SongStorageAdapter implements SongStoragePort {

    static final Duration PRESIGNED_URL_TTL = Duration.ofMinutes(10);

    private static final Logger logger = LoggerFactory.getLogger(S3SongStorageAdapter.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsProperties awsProperties;

    @Override
    public PresignedUploadResult generateUploadUrl(final SongUploadCommand command) {
        return presignUpload(UUID.randomUUID().toString(), command);
    }

    @Override
    public PresignedUploadResult regenerateUploadUrl(final String storageKey, final SongUploadCommand command) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey is required");
        }
        return presignUpload(storageKey, command);
    }

    private PresignedUploadResult presignUpload(final String storageKey, final SongUploadCommand command) {
        final Instant expiresAt = Instant.now().plus(PRESIGNED_URL_TTL);

        if (command.requiresMultipart()) {
            return generateMultipartUpload(storageKey, command, expiresAt);
        }
        return generateSinglePutUpload(storageKey, command, expiresAt);
    }

    @Override
    public void confirmUpload(final ConfirmUploadCommand command) {
        if (command.isMultipart()) {
            completeMultipartUpload(command);
            return;
        }
        verifyObjectExists(command.storageKey());
    }

    @Override
    public StorageObjectHead headObject(final String storageKey) {
        try {
            final var head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket())
                    .key(storageKey)
                    .build());
            return new StorageObjectHead(head.contentType(), head.contentLength());
        } catch (NoSuchKeyException e) {
            throw new IllegalStateException("Uploaded object not found for storage key: " + storageKey, e);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new IllegalStateException("Uploaded object not found for storage key: " + storageKey, e);
            }
            throw e;
        }
    }

    @Override
    public void promoteObject(final String stagingKey, final String finalKey) {
        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(bucket())
                    .sourceKey(stagingKey)
                    .destinationBucket(bucket())
                    .destinationKey(finalKey)
                    .build());
        } catch (S3Exception e) {
            throw new IllegalStateException("Failed to promote object from " + stagingKey
                    + " to " + finalKey + ": " + e.getMessage(), e);
        }
        // Verify the promoted copy before removing the staging object.
        headObject(finalKey);
        deleteObject(stagingKey);
    }

    @Override
    public void deleteObject(final String storageKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket())
                    .key(storageKey)
                    .build());
        } catch (Exception e) {
            logger.warn("Failed to delete storage object {}: {}", storageKey, e.getMessage(), e);
        }
    }

    @Override
    public void abortUpload(final String storageKey, final String multipartUploadId) {
        if (multipartUploadId == null || multipartUploadId.isBlank()) {
            // Single-part presigned uploads do not create an object until the client PUTs it,
            // so there is nothing to clean up.
            return;
        }
        try {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucket())
                    .key(storageKey)
                    .uploadId(multipartUploadId)
                    .build());
        } catch (Exception e) {
            logger.warn("Failed to abort orphan multipart upload for storage key {}: {}",
                    storageKey, e.getMessage(), e);
        }
    }

    @Override
    public URI getStreamingUrl(final String storageKey) {
        final GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket())
                .key(storageKey)
                .build();

        final GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_TTL)
                .getObjectRequest(getObjectRequest)
                .build();

        final PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return toUri(presignedRequest.url().toString());
    }

    private PresignedUploadResult generateSinglePutUpload(
            final String storageKey,
            final SongUploadCommand command,
            final Instant expiresAt
    ) {
        final PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket())
                .key(storageKey)
                .contentType(command.contentType())
                .contentLength(command.contentLengthBytes())
                .build();

        final PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(PRESIGNED_URL_TTL)
                        .putObjectRequest(putObjectRequest)
                        .build()
        );

        return new PresignedUploadResult(
                storageKey,
                null,
                expiresAt,
                false,
                List.of(new PresignedUploadPart(1, presigned.url().toString()))
        );
    }

    private PresignedUploadResult generateMultipartUpload(
            final String storageKey,
            final SongUploadCommand command,
            final Instant expiresAt
    ) {
        final CreateMultipartUploadResponse created = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                        .bucket(bucket())
                        .key(storageKey)
                        .contentType(command.contentType())
                        .build()
        );

        final int partCount = command.partCount();
        final List<PresignedUploadPart> parts = new ArrayList<>(partCount);
        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            final UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucket())
                    .key(storageKey)
                    .uploadId(created.uploadId())
                    .partNumber(partNumber)
                    .build();

            final PresignedUploadPartRequest presigned = s3Presigner.presignUploadPart(
                    UploadPartPresignRequest.builder()
                            .signatureDuration(PRESIGNED_URL_TTL)
                            .uploadPartRequest(uploadPartRequest)
                            .build()
            );
            parts.add(new PresignedUploadPart(partNumber, presigned.url().toString()));
        }

        return new PresignedUploadResult(
                storageKey,
                created.uploadId(),
                expiresAt,
                true,
                parts
        );
    }

    private void completeMultipartUpload(final ConfirmUploadCommand command) {
        if (command.completedParts().isEmpty()) {
            throw new IllegalArgumentException("Multipart confirmation requires completed parts with ETags.");
        }

        final List<CompletedPart> s3Parts = command.completedParts().stream()
                .sorted(Comparator.comparingInt(CompletedUploadPart::partNumber))
                .map(part -> CompletedPart.builder()
                        .partNumber(part.partNumber())
                        .eTag(part.eTag())
                        .build())
                .toList();

        s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(bucket())
                .key(command.storageKey())
                .uploadId(command.multipartUploadId())
                .multipartUpload(CompletedMultipartUpload.builder().parts(s3Parts).build())
                .build());
    }

    private void verifyObjectExists(final String storageKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket())
                    .key(storageKey)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new IllegalStateException("Uploaded object not found for storage key: " + storageKey, e);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new IllegalStateException("Uploaded object not found for storage key: " + storageKey, e);
            }
            throw e;
        }
    }

    private String bucket() {
        return awsProperties.s3().bucketName();
    }

    private static URI toUri(final String url) {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Failed to generate storage URL", e);
        }
    }
}
