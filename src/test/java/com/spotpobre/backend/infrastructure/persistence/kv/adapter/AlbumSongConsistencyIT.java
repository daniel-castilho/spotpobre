package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.AbstractIntegrationTest;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class AlbumSongConsistencyIT extends AbstractIntegrationTest {

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private SongMetadataRepository songMetadataRepository;

    @Test
    void albumSongsQueryReflectsUploadedSong() {
        AlbumId albumId = AlbumId.generate();
        Album album = Album.builder()
                .id(albumId)
                .name("Consistency Album")
                .artistId(ArtistId.generate())
                .build();
        albumRepository.save(album);

        Song song = Song.create("Consistency Song", albumId, "storage-key-consistency");
        songMetadataRepository.save(song);

        PageResult<Song> songs = songMetadataRepository.findByAlbumId(albumId, PageRequest.of(0, 20));

        assertEquals(1, songs.content().size());
        assertEquals(song.getId(), songs.content().get(0).getId());
        assertEquals("Consistency Song", songs.content().get(0).getTitle());
    }
}