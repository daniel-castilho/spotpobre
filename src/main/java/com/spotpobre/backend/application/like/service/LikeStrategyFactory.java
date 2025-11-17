package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.domain.like.model.EntityType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LikeStrategyFactory {

    private final List<LikeStrategy> strategies;

    public LikeStrategyFactory(List<LikeStrategy> strategies) {
        this.strategies = strategies;
    }

    public LikeStrategy getStrategy(EntityType entityType) {
        return strategies.stream()
               .filter(s -> s.supports(entityType))
               .findFirst()
               .orElseThrow(() -> new IllegalArgumentException("No like strategy found for type: " + entityType));
    }
}
