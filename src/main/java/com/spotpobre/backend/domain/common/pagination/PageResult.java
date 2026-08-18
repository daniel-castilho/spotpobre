package com.spotpobre.backend.domain.common.pagination;

import java.util.List;
import java.util.function.Function;

/**
 * Pure domain pagination result. Free of any framework or infrastructure dependency.
 *
 * <p>Supports both offset-based pagination (via {@code totalElements} / {@code totalPages} /
 * {@code hasNext} / {@code hasPrevious}) and cursor-based pagination (via {@code nextPageToken}).
 * Cursor-based results cannot know the total element count from DynamoDB, so {@code totalElements}
 * then reports the number of items returned on the current page.
 */
public record PageResult<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize,
        boolean hasNext,
        boolean hasPrevious,
        String nextPageToken
) {

    public PageResult {
        content = content == null ? List.of() : List.copyOf(content);
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be at least 1");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must not be negative");
        }
    }

    public static <T> PageResult<T> empty(int pageNumber, int pageSize) {
        return new PageResult<>(List.of(), 0L, 0, pageNumber, pageSize, false, pageNumber > 0, null);
    }

    public <R> PageResult<R> map(Function<? super T, R> mapper) {
        List<R> mappedContent = content.stream().map(mapper).toList();
        return new PageResult<>(
                mappedContent,
                totalElements,
                totalPages,
                pageNumber,
                pageSize,
                hasNext,
                hasPrevious,
                nextPageToken
        );
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }
}