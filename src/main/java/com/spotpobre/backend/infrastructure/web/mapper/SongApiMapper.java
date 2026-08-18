package com.spotpobre.backend.infrastructure.web.mapper;

import com.spotpobre.backend.application.song.port.in.InitiateSongUploadUseCase.InitiateSongUploadResult;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.song.model.PresignedUploadPart;
import com.spotpobre.backend.domain.song.model.PresignedUploadResult;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.infrastructure.persistence.kv.mapper.UuidMapper;
import com.spotpobre.backend.infrastructure.web.dto.response.InitiateSongUploadResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.PresignedUploadPartResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.SongDetailsResponse;
import com.spotpobre.backend.infrastructure.web.dto.response.SongResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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

    default Page<SongResponse> toResponsePage(final PageResult<Song> page, final Pageable pageable) {
        return new PageImpl<>(
                page.content().stream().map(this::toSongResponse).collect(Collectors.toList()),
                pageable,
                page.totalElements()
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

    default PresignedUploadPartResponse toPartResponse(final PresignedUploadPart part) {
        return new PresignedUploadPartResponse(part.partNumber(), part.url());
    }
}
