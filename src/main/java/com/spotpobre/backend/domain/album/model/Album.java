package com.spotpobre.backend.domain.album.model;

import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.song.model.Song;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(toBuilder = true)
public class Album {
    private final AlbumId id;
    private final ArtistId artistId;
    private String name;
    private String coverArtUrl;
    private List<Song> songs;
}
