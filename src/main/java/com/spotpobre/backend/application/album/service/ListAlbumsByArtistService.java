package com.spotpobre.backend.application.album.service;

import com.spotpobre.backend.application.album.port.in.ListAlbumsByArtistUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ListAlbumsByArtistService implements ListAlbumsByArtistUseCase {

    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResult<Album> listAlbumsByArtist(final ListAlbumsByArtistCommand command) {
        if (command.pageRequest().pageSize() > PageRequest.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must not exceed " + PageRequest.MAX_PAGE_SIZE);
        }
        // Unknown artist is a 404, not an empty page: the path identifies a resource.
        artistRepository.findById(command.artistId())
                .orElseThrow(() -> new NotFoundException("Artist not found"));

        return albumRepository.findByArtistId(command.artistId(), command.pageRequest(), command.cursor());
    }
}
