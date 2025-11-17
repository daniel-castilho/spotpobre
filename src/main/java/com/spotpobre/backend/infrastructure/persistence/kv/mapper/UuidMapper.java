package com.spotpobre.backend.infrastructure.persistence.kv.mapper;

import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.playlist.model.PlaylistId;
import com.spotpobre.backend.domain.song.model.SongId;
import com.spotpobre.backend.domain.user.model.UserId;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Mapper(componentModel = "spring")
public abstract class UuidMapper {

    @Named("uuidToString")
    public String uuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }

    @Named("stringToSongId")
    public SongId stringToSongId(String uuidString) {
        return uuidString != null ? new SongId(UUID.fromString(uuidString)) : null;
    }

    @Named("stringToArtistId")
    public ArtistId stringToArtistId(String uuidString) {
        return uuidString != null ? new ArtistId(UUID.fromString(uuidString)) : null;
    }

    @Named("stringToUserId")
    public UserId stringToUserId(String uuidString) {
        return uuidString != null ? new UserId(UUID.fromString(uuidString)) : null;
    }

    @Named("stringToPlaylistId")
    public PlaylistId stringToPlaylistId(String uuidString) {
        return uuidString != null ? new PlaylistId(UUID.fromString(uuidString)) : null;
    }

    @Named("playlistIdToUuid")
    public UUID playlistIdToUuid(PlaylistId playlistId) {
        return playlistId != null ? playlistId.value() : null;
    }

    @Named("userIdToUuid")
    public UUID userIdToUuid(UserId userId) {
        return userId != null ? userId.value() : null;
    }

    @Named("songIdToUuid")
    public UUID songIdToUuid(SongId songId) {
        return songId != null ? songId.value() : null;
    }

    @Named("artistIdToUuid")
    public UUID artistIdToUuid(ArtistId artistId) {
        return artistId != null ? artistId.value() : null;
    }

    @Named("uuidToArtistId") // New method
    public ArtistId uuidToArtistId(UUID uuid) {
        return uuid != null ? new ArtistId(uuid) : null;
    }
}
