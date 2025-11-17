package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.application.like.port.in.ToggleLikeUseCase;
import com.spotpobre.backend.domain.like.model.Like;
import com.spotpobre.backend.domain.like.port.LikeRepository;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

@RequiredArgsConstructor
public class ToggleLikeService implements ToggleLikeUseCase {

    private final LikeRepository likeRepository;
    private final LikeStrategyFactory likeStrategyFactory;

    @Override
    public LikeResult toggleLike(ToggleLikeCommand command) {
        LikeStrategy strategy = likeStrategyFactory.getStrategy(command.entityType());
        strategy.validateEntityExists(command.entityId());

        boolean exists = likeRepository.findById(command.userId(), command.entityId(), command.entityType()).isPresent();

        boolean isNowLiked;

        if (exists) {
            likeRepository.delete(command.userId(), command.entityId(), command.entityType());
            isNowLiked = false;
        } else {
            Like newLike = new Like(command.userId(), command.entityId(), command.entityType(), Instant.now());
            likeRepository.save(newLike);
            isNowLiked = true;
        }

        long newLikeCount = likeRepository.countLikesByEntity(command.entityId(), command.entityType());

        return new LikeResult(isNowLiked, newLikeCount);
    }
}
