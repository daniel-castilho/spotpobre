package com.spotpobre.backend.infrastructure.web.mapper;

import com.spotpobre.backend.application.song.port.in.InitiateSongUploadUseCase.InitiateSongUploadResult;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.song.model.PresignedUploadPart;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.UuidMapper;
import com.spotpobre.backend.infrastructure.web.dto.response.InitiateSongUploadResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.PageResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.PresignedUploadPartResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.SongDetailsResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.SongResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.net.URI;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = UuidMapper.class)
public interface SongApiMapper {

    @Mapping(source = "song.id", target = "id", qualifiedByName = "songIdToUuid")
    @Mapping(source = "song.albumId", target = "albumId", qualifiedByName = "albumIdToUuid") // Changed
    @Mapping(source = "streamingUrl", target = "streamingUrl", qualifiedByName = "uriToString")
    SongDetailsResponse toResponse(final Song song, final URI streamingUrl);

    @Mapping(source = "id", target = "id", qualifiedByName = "songIdToUuid")
    @Mapping(source = "albumId", target = "albumId", qualifiedByName = "albumIdToUuid") // Changed
    SongResponse toSongResponse(final Song song);

    default PageResponse<SongResponse> toPageResponse(final PageResult<Song> page) {
        return new PageResponse<>(
                page.content().stream().map(this::toSongResponse).collect(Collectors.toList()),
                page.nextPageToken(),
                page.hasNext()
        );
    }

    default InitiateSongUploadResponse toInitiateResponse(final InitiateSongUploadResult result) {
        final Song song = result.song();
        final PresignedUploadResult upload = result.upload();
        return new InitiateSongUploadResponse(
                song.getId().value(),
                song.getTitle(),
                song.getAlbumId().value(),
                upload.storageKey(),
                upload.multipartUploadId(),
                upload.expiresAt(),
                upload.multipart(),
                upload.parts().stream().map(this::toPartResponse).toList()
        );
    }

    default InitiateSongUploadResponse toStagedInitiateResponse(
            final com.spotpobre.backend.application.song.port.in.InitiateSongUploadIdempotentlyUseCase.InitiateUploadIdempotentResult result) {
        final var staged = result.upload();
        final var presigned = result.presigned();
        return new InitiateSongUploadResponse(
                staged.getSongId().value(),
                staged.getTitle(),
                staged.getAlbumId().value(),
                presigned.storageKey(),
                presigned.multipartUploadId(),
                presigned.expiresAt(),
                presigned.multipart(),
                presigned.parts().stream().map(this::toPartResponse).toList()
        );
    }

    default PresignedUploadPartResponse toPartResponse(final PresignedUploadPart part) {
        return new PresignedUploadPartResponse(part.partNumber(), part.url());
    }
}
