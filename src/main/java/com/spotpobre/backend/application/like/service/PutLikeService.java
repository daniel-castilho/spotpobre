package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.application.like.port.in.PutLikeUseCase;
import com.spotpobre.backend.domain.like.model.Like;
import com.spotpobre.backend.domain.like.port.LikeRepository;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

@RequiredArgsConstructor
public class PutLikeService implements PutLikeUseCase {

    private final LikeRepository likeRepository;
    private final LikeStrategyFactory likeStrategyFactory;

    @Override
    public void putLike(PutLikeCommand command) {
        LikeStrategy strategy = likeStrategyFactory.getStrategy(command.entityType());
        strategy.validateEntityExists(command.entityId().toString());

        likeRepository.createIfAbsent(
                new Like(command.userId(), command.entityId().toString(), command.entityType(), Instant.now()));
    }
}
