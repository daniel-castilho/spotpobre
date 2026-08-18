package com.spotpobre.backend.domain.common.pagination;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageResultTest {

    @Test
    void constructor_nullContentBecomesEmpty() {
        PageResult<String> result = new PageResult<>(null, 0L, 0, 0, 10, false, false, null);
        assertTrue(result.isEmpty());
        assertTrue(result.content().isEmpty());
    }

    @Test
    void constructor_invalidPaging_throws() {
        assertThrows(IllegalArgumentException.class, () -> new PageResult<>(List.of(), -1L, 0, 0, 10, false, false, null));
        assertThrows(IllegalArgumentException.class, () -> new PageResult<>(List.of(), 0L, 0, -1, 10, false, false, null));
        assertThrows(IllegalArgumentException.class, () -> new PageResult<>(List.of(), 0L, 0, 0, 0, false, false, null));
    }

    @Test
    void empty_createsEmptyPageWithPagingState() {
        PageResult<String> result = PageResult.empty(0, 10);
        assertTrue(result.isEmpty());
        assertEquals(0L, result.totalElements());
        assertEquals(0, result.totalPages());
        assertFalse(result.hasNext());
        assertFalse(result.hasPrevious());
        assertNull(result.nextPageToken());

        PageResult<String> secondPage = PageResult.empty(2, 10);
        assertTrue(secondPage.hasPrevious());
    }

    @Test
    void map_transformsContentAndPreservesMetadata() {
        PageResult<Integer> result = new PageResult<>(List.of(1, 2), 2L, 1, 0, 2, false, false, null);

        PageResult<String> mapped = result.map(String::valueOf);

        assertEquals(List.of("1", "2"), mapped.content());
        assertEquals(2L, mapped.totalElements());
        assertEquals(1, mapped.totalPages());
        assertEquals(0, mapped.pageNumber());
        assertEquals(2, mapped.pageSize());
        assertFalse(mapped.hasNext());
        assertFalse(mapped.hasPrevious());
        assertNull(mapped.nextPageToken());
    }

    @Test
    void map_preservesCursorToken() {
        PageResult<Integer> result = new PageResult<>(List.of(1), 1L, 1, 0, 2, true, false, "token");
        PageResult<String> mapped = result.map(String::valueOf);
        assertEquals("token", mapped.nextPageToken());
        assertTrue(mapped.hasNext());
    }
}