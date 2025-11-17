package com.spotpobre.backend.infrastructure.storage.s3;

import com.spotpobre.backend.domain.song.model.SongFile;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3SongStorageAdapter implements SongStoragePort {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Override
    public String saveSong(final SongFile file) {
        final String key = UUID.randomUUID().toString();
        final PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.contentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.content()));
        return key;
    }

    @Override
    public URI getStreamingUrl(final SongId id) {
        final GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(id.value().toString()) // Assuming the storageId is the same as the SongId
                .build();

        final GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(getObjectRequest)
                .build();

        final PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        try {
            return presignedRequest.url().toURI(); // Convert URL to URI
        } catch (java.net.URISyntaxException e) {
            throw new RuntimeException("Failed to generate streaming URL", e);
        }
    }
}
