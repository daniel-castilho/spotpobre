package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.application.like.port.in.ToggleLikeUseCase;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.like.model.Like;
import com.spotpobre.backend.domain.like.port.LikeRepository;
import com.spotpobre.backend.domain.user.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToggleLikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private LikeStrategyFactory likeStrategyFactory;

    @Mock
    private LikeStrategy likeStrategy;

    @InjectMocks
    private ToggleLikeService toggleLikeService;

    private ToggleLikeUseCase.ToggleLikeCommand command(UserId userId, String entityId) {
        return new ToggleLikeUseCase.ToggleLikeCommand(userId, entityId, EntityType.SONG);
    }

    @Test
    void toggleLike_whenNotLiked_shouldSaveAndReturnLiked() {
        // Given
        UserId userId = UserId.generate();
        String entityId = UUID.randomUUID().toString();
        when(likeStrategyFactory.getStrategy(EntityType.SONG)).thenReturn(likeStrategy);
        when(likeRepository.findById(userId, entityId, EntityType.SONG)).thenReturn(Optional.empty());
        when(likeRepository.countLikesByEntity(entityId, EntityType.SONG)).thenReturn(1L);

        // When
        ToggleLikeUseCase.LikeResult result = toggleLikeService.toggleLike(command(userId, entityId));

        // Then
        assertTrue(result.isLiked());
        assertEquals(1L, result.newLikeCount());
        verify(likeStrategy).validateEntityExists(entityId);
        verify(likeRepository).save(any(Like.class));
        verify(likeRepository, never()).delete(any(), any(), any());
    }

    @Test
    void toggleLike_whenAlreadyLiked_shouldDeleteAndReturnNotLiked() {
        // Given
        UserId userId = UserId.generate();
        String entityId = UUID.randomUUID().toString();
        Like existingLike = new Like(userId, entityId, EntityType.SONG, Instant.now());
        when(likeStrategyFactory.getStrategy(EntityType.SONG)).thenReturn(likeStrategy);
        when(likeRepository.findById(userId, entityId, EntityType.SONG)).thenReturn(Optional.of(existingLike));
        when(likeRepository.countLikesByEntity(entityId, EntityType.SONG)).thenReturn(0L);

        // When
        ToggleLikeUseCase.LikeResult result = toggleLikeService.toggleLike(command(userId, entityId));

        // Then
        assertFalse(result.isLiked());
        assertEquals(0L, result.newLikeCount());
        verify(likeStrategy).validateEntityExists(entityId);
        verify(likeRepository).delete(userId, entityId, EntityType.SONG);
        verify(likeRepository, never()).save(any(Like.class));
    }

    @Test
    void toggleLike_whenEntityDoesNotExist_shouldThrowAndNotTouchRepository() {
        // Given
        UserId userId = UserId.generate();
        String entityId = UUID.randomUUID().toString();
        when(likeStrategyFactory.getStrategy(EntityType.SONG)).thenReturn(likeStrategy);
        doThrow(new NotFoundException("Song not found: " + entityId))
                .when(likeStrategy).validateEntityExists(entityId);

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            toggleLikeService.toggleLike(command(userId, entityId));
        });

        assertEquals("Song not found: " + entityId, exception.getMessage());
        verify(likeRepository, never()).save(any(Like.class));
        verify(likeRepository, never()).delete(any(), any(), any());
        verify(likeRepository, never()).countLikesByEntity(any(), any());
    }
}