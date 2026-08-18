package com.spotpobre.backend.domain.common.pagination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageRequestTest {

    @Test
    void constructor_negativePageNumber_throws() {
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(-1, 10));
    }

    @Test
    void constructor_pageSizeBelowOne_throws() {
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(0, 0));
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(0, -5));
    }

    @Test
    void of_defaultsToUnsorted() {
        PageRequest request = PageRequest.of(2, 25);
        assertEquals(2, request.pageNumber());
        assertEquals(25, request.pageSize());
        assertNull(request.sort());
    }

    @Test
    void offset_isPageNumberTimesPageSize() {
        assertEquals(0L, PageRequest.of(0, 10).offset());
        assertEquals(50L, PageRequest.of(5, 10).offset());
    }

    @Test
    void sort_factories_buildAscendingAndDescending() {
        assertEquals(PageRequest.Sort.Direction.ASC, PageRequest.Sort.asc("name").direction());
        assertEquals(PageRequest.Sort.Direction.DESC, PageRequest.Sort.desc("title").direction());
        assertEquals("name", PageRequest.Sort.asc("name").property());
    }
}