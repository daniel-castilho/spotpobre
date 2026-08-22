package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.artist.port.in.RequireArtistAccessUseCase;
import com.spotpobre.backend.application.song.port.in.ConfirmSongUploadUseCase;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.song.model.ConfirmUploadCommand;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;

public class ConfirmSongUploadService implements ConfirmSongUploadUseCase {

    private final SongStoragePort songStoragePort;
    private final SongMetadataRepository songMetadataRepository;
    private final AlbumRepository albumRepository;
    private final RequireArtistAccessUseCase requireArtistAccess;

    public ConfirmSongUploadService(
            final SongStoragePort songStoragePort,
            final SongMetadataRepository songMetadataRepository,
            final AlbumRepository albumRepository,
            final RequireArtistAccessUseCase requireArtistAccess
    ) {
        this.songStoragePort = songStoragePort;
        this.songMetadataRepository = songMetadataRepository;
        this.albumRepository = albumRepository;
        this.requireArtistAccess = requireArtistAccess;
    }

    @Override
    public Song confirmUpload(final ConfirmSongUploadCommand command) {
        final Song song = songMetadataRepository.findById(command.songId())
                .orElseThrow(() -> new NotFoundException("Song not found: " + command.songId()));

        if (!song.getAlbumId().equals(command.albumId())) {
            throw new NotFoundException("Song does not belong to album: " + command.albumId());
        }
        final var album = albumRepository.findById(song.getAlbumId())
                .orElseThrow(() -> new NotFoundException("Album not found: " + song.getAlbumId()));
        requireArtistAccess.requireAccess(
                new RequireArtistAccessUseCase.ActorArtistRef(command.actorUserId(), command.actorIsAdmin()),
                album.getArtistId());
        if (!song.getStorageId().equals(command.storageKey())) {
            throw new NotFoundException("Storage key does not match the song record.");
        }

        songStoragePort.confirmUpload(new ConfirmUploadCommand(
                command.storageKey(),
                command.multipartUploadId(),
                command.completedParts()
        ));

        return song;
    }
}
