package com.spotpobre.backend.infrastructure.storage.s3;

import com.spotpobre.backend.AbstractIntegrationTest;
import com.spotpobre.backend.domain.song.model.ConfirmUploadCommand;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.SongUploadCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class S3SongStorageAdapterTest extends AbstractIntegrationTest {

    @Autowired
    private S3SongStorageAdapter adapter;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void shouldUploadAndGenerateValidStreamingUrl() throws Exception {
        byte[] audioBytes = "fake-mp3-audio-content".getBytes();
        SongUploadCommand command = new SongUploadCommand("audio/mpeg", audioBytes.length);

        PresignedUploadResult uploadResult = adapter.generateUploadUrl(command);
        assertNotNull(uploadResult.storageKey());
        assertEquals(1, uploadResult.parts().size());

        String presignedPutUrl = uploadResult.parts().get(0).url();
        uploadObject(presignedPutUrl, audioBytes);

        ConfirmUploadCommand confirmCommand = new ConfirmUploadCommand(
                uploadResult.storageKey(),
                uploadResult.multipartUploadId(),
                java.util.List.of()
        );
        assertDoesNotThrow(() -> adapter.confirmUpload(confirmCommand));

        URI streamingUrl = adapter.getStreamingUrl(uploadResult.storageKey());
        assertNotNull(streamingUrl);

        HttpResponse<byte[]> downloadResponse = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(streamingUrl)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );

        assertEquals(200, downloadResponse.statusCode());
        assertArrayEquals(audioBytes, downloadResponse.body());
        assertEquals("audio/mpeg", downloadResponse.headers()
                .firstValue("Content-Type").orElse(""));
    }

    private void uploadObject(String presignedUrl, byte[] data) throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(presignedUrl))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                        .header("Content-Type", "audio/mpeg")
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, response.statusCode());
    }
}
