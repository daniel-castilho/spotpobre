package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.AbstractIntegrationTest;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.user.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DynamoDbPlaylistRepositoryAdapterTest extends AbstractIntegrationTest {

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @BeforeEach
    void setUp() {
        // Ensure the table exists before each test
        // In a real scenario, you might use a more sophisticated schema migration tool
        ListTablesResponse tables = dynamoDbClient.listTables();
        boolean tableExists = tables.tableNames().stream().anyMatch(name -> name.equals("Playlists"));
        if (!tableExists) {
            dynamoDbEnhancedClient.table("Playlists", TableSchema.fromBean(com.spotpobre.backend.infrastructure.persistence.kv.entity.PlaylistDocument.class)).createTable();
        }
    }

    @Test
    void shouldSaveAndFindPlaylistById() {
        // Given
        UserId ownerId = UserId.generate();
        Playlist playlist = Playlist.create("My Test Playlist", ownerId);

        // When
        playlistRepository.save(playlist);
        Playlist foundPlaylist = playlistRepository.findById(playlist.getId()).orElse(null);

        // Then
        assertNotNull(foundPlaylist);
        assertEquals(playlist.getId(), foundPlaylist.getId());
        assertEquals("My Test Playlist", foundPlaylist.getName());
        assertEquals(ownerId, foundPlaylist.getOwnerId());
    }
}
