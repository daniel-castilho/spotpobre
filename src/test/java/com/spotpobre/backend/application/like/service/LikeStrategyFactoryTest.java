package com.spotpobre.backend.application.like.service;

import com.spotpobre.backend.domain.like.model.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LikeStrategyFactoryTest {

    @Test
    void shouldReturnStrategyThatSupportsEntityType() {
        // Given
        LikeStrategy songStrategy = mock(LikeStrategy.class);
        LikeStrategy artistStrategy = mock(LikeStrategy.class);
        when(songStrategy.supports(EntityType.SONG)).thenReturn(true);
        when(artistStrategy.supports(EntityType.SONG)).thenReturn(false);

        LikeStrategyFactory factory = new LikeStrategyFactory(List.of(artistStrategy, songStrategy));

        // When
        LikeStrategy result = factory.getStrategy(EntityType.SONG);

        // Then
        assertSame(songStrategy, result);
    }

    @Test
    void shouldThrowWhenNoStrategySupportsEntityType() {
        // Given
        LikeStrategy songStrategy = mock(LikeStrategy.class);
        when(songStrategy.supports(EntityType.ARTIST)).thenReturn(false);

        LikeStrategyFactory factory = new LikeStrategyFactory(List.of(songStrategy));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            factory.getStrategy(EntityType.ARTIST);
        });

        assertEquals("No like strategy found for type: ARTIST", exception.getMessage());
    }
}