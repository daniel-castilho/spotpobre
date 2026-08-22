package com.spotpobre.backend.application.song.service;

import com.spotpobre.backend.application.song.port.in.InitiateSongUploadUseCase;
import com.spotpobre.backend.application.artist.port.in.RequireArtistAccessUseCase;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.model.SongUploadCommand;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InitiateSongUploadService implements InitiateSongUploadUseCase {

    private static final Logger logger = LoggerFactory.getLogger(InitiateSongUploadService.class);

    private final SongStoragePort songStoragePort;
    private final SongMetadataRepository songMetadataRepository;
    private final AlbumRepository albumRepository;
    private final RequireArtistAccessUseCase requireArtistAccess;

    public InitiateSongUploadService(
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
    public InitiateSongUploadResult initiateUpload(final InitiateSongUploadCommand command) {
        final var album = albumRepository.findById(command.albumId())
                .orElseThrow(() -> new NotFoundException("Album not found: " + command.albumId()));
        requireArtistAccess.requireAccess(
                new RequireArtistAccessUseCase.ActorArtistRef(command.actorUserId(), command.actorIsAdmin()),
                album.getArtistId());

        final SongUploadCommand uploadCommand = new SongUploadCommand(
                command.contentType(),
                command.contentLengthBytes()
        );
        final PresignedUploadResult upload = songStoragePort.generateUploadUrl(uploadCommand);

        final Song song = Song.create(command.title(), command.albumId(), upload.storageKey());

        try {
            songMetadataRepository.save(song);
        } catch (RuntimeException e) {
            // S3 and DynamoDB are separate systems: if metadata persistence fails after a multipart
            // upload was created in S3, abort it so no orphan upload is left behind. Single-part
            // presigned URLs have nothing to clean up. The failure is rethrown so the caller sees it.
            if (upload.multipartUploadId() != null) {
                songStoragePort.abortUpload(upload.storageKey(), upload.multipartUploadId());
            } else {
                logger.warn("Metadata save failed after generating presigned upload for key {}; " +
                        "no object was created yet, nothing to abort.", upload.storageKey());
            }
            throw e;
        }

        return new InitiateSongUploadResult(song, upload);
    }
}
