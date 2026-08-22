package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.application.like.port.in.DeleteLikeUseCase;
import com.spotpobre.backend.domain.like.port.LikeRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DeleteLikeService implements DeleteLikeUseCase {

    private final LikeRepository likeRepository;
    private final LikeStrategyFactory likeStrategyFactory;

    @Override
    public void deleteLike(DeleteLikeCommand command) {
        LikeStrategy strategy = likeStrategyFactory.getStrategy(command.entityType());
        strategy.validateEntityExists(command.entityId().toString());

        likeRepository.deleteIfPresent(command.userId(), command.entityId().toString(), command.entityType());
    }
}
