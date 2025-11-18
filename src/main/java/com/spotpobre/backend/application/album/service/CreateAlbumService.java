package com.spotpobre.backend.application.album.service;

import com.spotpobre.backend.application.album.port.in.CreateAlbumUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class CreateAlbumService implements CreateAlbumUseCase {

    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;

    @Override
    @Transactional
    public Album createAlbum(CreateAlbumCommand command) {
        // Validate that the artist exists
        artistRepository.findById(command.artistId())
                .orElseThrow(() -> new IllegalArgumentException("Artist not found: " + command.artistId()));

        // For now, we are not creating the songs, just the album structure.
        // A more complex implementation would create Song entities here.
        Album album = Album.builder()
                .id(com.spotpobre.backend.domain.album.model.AlbumId.generate())
                .name(command.name())
                .artistId(command.artistId())
                .coverArtUrl(command.coverArtUrl())
                .songs(new java.util.ArrayList<>())
                .build();

        albumRepository.save(album);

        return album;
    }
}
