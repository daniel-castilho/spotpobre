package com.spotpobre.backend.domain.song.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongUploadCommandTest {

    @Test
    void constructor_normalizesContentType() {
        SongUploadCommand command = new SongUploadCommand("Audio/MPEG", 2048L);
        assertEquals("audio/mpeg", command.contentType());
        assertFalse(command.requiresMultipart());
        assertEquals(1, command.partCount());
    }

    @Test
    void constructor_rejectsUnsupportedType() {
        assertThrows(IllegalArgumentException.class, () -> new SongUploadCommand("application/pdf", 10L));
    }

    @Test
    void constructor_rejectsOversizedFile() {
        assertThrows(IllegalArgumentException.class, () ->
                new SongUploadCommand("audio/mpeg", SongUploadCommand.MAX_CONTENT_LENGTH_BYTES + 1));
    }

    @Test
    void requiresMultipart_whenLargerThanThreshold() {
        SongUploadCommand command = new SongUploadCommand(
                "audio/flac",
                SongUploadCommand.MULTIPART_THRESHOLD_BYTES + 1
        );
        assertTrue(command.requiresMultipart());
        assertTrue(command.partCount() >= 3);
    }
}
