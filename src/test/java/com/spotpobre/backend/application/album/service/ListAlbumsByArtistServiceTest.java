package com.spotpobre.backend.application.album.service;

import com.spotpobre.backend.application.album.port.in.ListAlbumsByArtistUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAlbumsByArtistServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private AlbumRepository albumRepository;

    @InjectMocks
    private ListAlbumsByArtistService listAlbumsByArtistService;

    @Test
    void listing_knownArtist_delegatesToRepositoryPage() {
        Artist artist = Artist.create("Known Artist");
        when(artistRepository.findById(artist.getId())).thenReturn(Optional.of(artist));
        PageRequest pageRequest = PageRequest.of(0, 20);
        ListAlbumsByArtistUseCase.ListAlbumsByArtistCommand command =
                new ListAlbumsByArtistUseCase.ListAlbumsByArtistCommand(artist.getId(), pageRequest, null);
        PageResult<Album> expected = new PageResult<>(
                List.of(), 0, 1, 0, 20, false, false, null);
        when(albumRepository.findByArtistId(artist.getId(), pageRequest, null)).thenReturn(expected);

        assertSame(expected, listAlbumsByArtistService.listAlbumsByArtist(command));
    }

    @Test
    void listing_unknownArtist_answersNotFoundWithoutTouchingAlbumRepository() {
        ArtistId unknownId = new ArtistId(UUID.randomUUID());
        when(artistRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> listAlbumsByArtistService.listAlbumsByArtist(
                        new ListAlbumsByArtistUseCase.ListAlbumsByArtistCommand(unknownId, PageRequest.of(0, 20), null)));

        verify(albumRepository, never()).findByArtistId(any(), any(), any());
    }

    @Test
    void listing_pageSizeAboveMaximum_rejectsWith400Semantic() {
        Artist artist = Artist.create("Any Artist");
        ListAlbumsByArtistUseCase.ListAlbumsByArtistCommand command =
                new ListAlbumsByArtistUseCase.ListAlbumsByArtistCommand(
                        artist.getId(), PageRequest.of(0, PageRequest.MAX_PAGE_SIZE + 1), null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> listAlbumsByArtistService.listAlbumsByArtist(command));
        assertEquals("pageSize must not exceed " + PageRequest.MAX_PAGE_SIZE, exception.getMessage());
        verify(albumRepository, never()).findByArtistId(any(), any(), any());
    }
}
