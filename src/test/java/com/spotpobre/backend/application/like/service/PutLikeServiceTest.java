package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.application.like.port.in.PutLikeUseCase;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.like.model.Like;
import com.spotpobre.backend.domain.like.port.LikeRepository;
import com.spotpobre.backend.domain.user.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PutLikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private LikeStrategy likeStrategy;

    @Mock
    private LikeStrategyFactory likeStrategyFactory;

    @InjectMocks
    private PutLikeService putLikeService;

    @Test
    void shouldCreateLikeWhenAbsent() {
        UUID entityId = UUID.randomUUID();
        UserId userId = UserId.generate();
        when(likeStrategyFactory.getStrategy(EntityType.SONG)).thenReturn(likeStrategy);
        when(likeRepository.createIfAbsent(any(Like.class))).thenReturn(true);

        putLikeService.putLike(new PutLikeUseCase.PutLikeCommand(userId, entityId, EntityType.SONG));

        ArgumentCaptor<Like> likeCaptor = ArgumentCaptor.forClass(Like.class);
        verify(likeRepository, times(1)).createIfAbsent(likeCaptor.capture());
        Like saved = likeCaptor.getValue();
        assertEquals(userId, saved.userId());
        assertEquals(entityId.toString(), saved.entityId());
        assertEquals(EntityType.SONG, saved.entityType());
        assertNotNull(saved.likedAt());
    }

    @Test
    void shouldPreserveOriginalLikedAtWhenLikeAlreadyExists() {
        UUID entityId = UUID.randomUUID();
        UserId userId = UserId.generate();
        Instant originalLikedAt = Instant.parse("2020-01-01T00:00:00Z");
        when(likeStrategyFactory.getStrategy(EntityType.SONG)).thenReturn(likeStrategy);
        when(likeRepository.createIfAbsent(any(Like.class))).thenReturn(false);

        putLikeService.putLike(new PutLikeUseCase.PutLikeCommand(userId, entityId, EntityType.SONG));

        // The repository conditional create rejected the write, so the stored likedAt is untouched.
        verify(likeRepository, times(1)).createIfAbsent(any(Like.class));
    }

    @Test
    void shouldThrowNotFoundWhenTargetEntityDoesNotExist() {
        UUID missingEntityId = UUID.randomUUID();
        UserId userId = UserId.generate();
        when(likeStrategyFactory.getStrategy(EntityType.PLAYLIST)).thenReturn(likeStrategy);
        doThrow(new NotFoundException("Playlist not found"))
                .when(likeStrategy).validateEntityExists(missingEntityId.toString());

        assertThrows(NotFoundException.class, () -> putLikeService.putLike(
                new PutLikeUseCase.PutLikeCommand(userId, missingEntityId, EntityType.PLAYLIST)));

        verify(likeRepository, never()).createIfAbsent(any());
    }
}
