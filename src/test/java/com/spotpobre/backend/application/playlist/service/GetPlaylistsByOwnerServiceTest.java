package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.user.model.UserId;
import com.spotpobre.backend.infrastructure.persistence.kv.model.DynamoDbPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetPlaylistsByOwnerServiceTest {

    @Mock
    private PlaylistRepository playlistRepository;

    @InjectMocks
    private GetPlaylistsByOwnerService getPlaylistsByOwnerService;

    @Test
    void shouldGetPlaylistsByOwner() {
        // Given
        UserId ownerId = UserId.generate();
        Pageable pageable = PageRequest.of(0, 10);
        String token = "next-page-token";
        DynamoDbPage<Playlist> expectedPage = new DynamoDbPage<>(Collections.emptyList(), token);

        when(playlistRepository.findByOwnerId(ownerId, pageable, token)).thenReturn(expectedPage);

        // When
        DynamoDbPage<Playlist> resultPage = getPlaylistsByOwnerService.getPlaylistsByOwner(ownerId, pageable, token);

        // Then
        assertNotNull(resultPage);
        assertEquals(expectedPage, resultPage);
        verify(playlistRepository, times(1)).findByOwnerId(ownerId, pageable, token);
    }
}
