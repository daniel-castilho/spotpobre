package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.application.like.port.in.DeleteLikeUseCase;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.like.model.EntityType;
import com.spotpobre.backend.domain.like.port.LikeRepository;
import com.spotpobre.backend.domain.user.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteLikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private LikeStrategy likeStrategy;

    @Mock
    private LikeStrategyFactory likeStrategyFactory;

    @InjectMocks
    private DeleteLikeService deleteLikeService;

    @Test
    void shouldDeleteLikeWhenPresent() {
        UUID entityId = UUID.randomUUID();
        UserId userId = UserId.generate();
        when(likeStrategyFactory.getStrategy(EntityType.SONG)).thenReturn(likeStrategy);
        when(likeRepository.deleteIfPresent(userId, entityId.toString(), EntityType.SONG)).thenReturn(true);

        deleteLikeService.deleteLike(new DeleteLikeUseCase.DeleteLikeCommand(userId, entityId, EntityType.SONG));

        verify(likeRepository, times(1)).deleteIfPresent(userId, entityId.toString(), EntityType.SONG);
    }

    @Test
    void shouldBeSuccessfulNoOpWhenLikeAlreadyAbsent() {
        UUID entityId = UUID.randomUUID();
        UserId userId = UserId.generate();
        when(likeStrategyFactory.getStrategy(EntityType.SONG)).thenReturn(likeStrategy);
        when(likeRepository.deleteIfPresent(userId, entityId.toString(), EntityType.SONG)).thenReturn(false);

        deleteLikeService.deleteLike(new DeleteLikeUseCase.DeleteLikeCommand(userId, entityId, EntityType.SONG));

        verify(likeRepository, times(1)).deleteIfPresent(userId, entityId.toString(), EntityType.SONG);
    }

    @Test
    void shouldThrowNotFoundWhenTargetEntityDoesNotExist() {
        UUID missingEntityId = UUID.randomUUID();
        UserId userId = UserId.generate();
        when(likeStrategyFactory.getStrategy(EntityType.ARTIST)).thenReturn(likeStrategy);
        doThrow(new NotFoundException("Artist not found"))
                .when(likeStrategy).validateEntityExists(missingEntityId.toString());

        assertThrows(NotFoundException.class, () -> deleteLikeService.deleteLike(
                new DeleteLikeUseCase.DeleteLikeCommand(userId, missingEntityId, EntityType.ARTIST)));

        verify(likeRepository, never()).deleteIfPresent(any(), any(), any());
    }
}
