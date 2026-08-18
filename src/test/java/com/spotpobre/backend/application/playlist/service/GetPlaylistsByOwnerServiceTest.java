package com.spotpobre.backend.application.playlist.service;

import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.playlist.model.Playlist;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.user.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        PageRequest pageRequest = PageRequest.of(0, 10);
        String token = "next-page-token";
        PageResult<Playlist> expectedPage = new PageResult<>(Collections.emptyList(), 0L, 0, 0, 10, true, false, token);

        when(playlistRepository.findByOwnerId(ownerId, pageRequest, token)).thenReturn(expectedPage);

        // When
        PageResult<Playlist> resultPage = getPlaylistsByOwnerService.getPlaylistsByOwner(ownerId, pageRequest, token);

        // Then
        assertNotNull(resultPage);
        assertEquals(expectedPage, resultPage);
        verify(playlistRepository, times(1)).findByOwnerId(ownerId, pageRequest, token);
    }
}
