package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.ListArtistsUseCase;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListArtistsServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @InjectMocks
    private ListArtistsService listArtistsService;

    @Test
    void listing_delegatesToRepositoryScan() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        ListArtistsUseCase.ListArtistsCommand command = new ListArtistsUseCase.ListArtistsCommand(pageRequest, null);
        PageResult<Artist> expected = new PageResult<>(
                List.of(), 0, 1, 0, 20, false, false, null);
        when(artistRepository.findAll(pageRequest, null)).thenReturn(expected);

        assertSame(expected, listArtistsService.listArtists(command));
        verify(artistRepository).findAll(pageRequest, null);
    }

    @Test
    void listing_pageSizeAboveMaximum_rejectsWith400Semantic() {
        ListArtistsUseCase.ListArtistsCommand command = new ListArtistsUseCase.ListArtistsCommand(
                PageRequest.of(0, PageRequest.MAX_PAGE_SIZE + 1), null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> listArtistsService.listArtists(command));
        assertEquals("pageSize must not exceed " + PageRequest.MAX_PAGE_SIZE, exception.getMessage());
        verify(artistRepository, never()).findAll(any(), any());
    }
}
