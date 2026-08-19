package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.AbstractIntegrationTest;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.song.model.Song;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SongSearchPaginationIT extends AbstractIntegrationTest {

    @Autowired
    private SongMetadataRepository songMetadataRepository;

    @Test
    void searchShouldBeCaseInsensitiveAndReturnMixedCaseMatches() {
        String suffix = "zz" + UUID.randomUUID().toString().substring(0, 8);
        insertSong(suffix + " case test song");
        insertSong(suffix + " Case Test another");
        insertSong(suffix + " CASE TEST!");

        PageResult<Song> lower = songMetadataRepository.searchByTitle(
                suffix + " case test", PageRequest.of(0, 10), null);
        PageResult<Song> upper = songMetadataRepository.searchByTitle(
                suffix.toUpperCase() + " CASE TEST", PageRequest.of(0, 10), null);
        PageResult<Song> mixed = songMetadataRepository.searchByTitle(
                suffix + " CaSe TeSt", PageRequest.of(0, 10), null);

        assertEquals(3, lower.content().size());
        assertEquals(3, upper.content().size());
        assertEquals(3, mixed.content().size());
        assertEquals(
                titles(lower),
                titles(upper),
                "query casing must not change results"
        );
        assertEquals(
                titles(upper),
                titles(mixed),
                "query casing must not change results"
        );
    }

    @Test
    void paginationShouldReturnDifferentItemsAndWalkCursorToTheEnd() {
        String prefix = "pagewalk-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 1; i <= 5; i++) {
            insertSong(prefix + "-" + i);
        }

        PageResult<Song> page1 = songMetadataRepository.searchByTitle(prefix, PageRequest.of(0, 2), null);
        assertEquals(2, page1.content().size());
        assertTrue(page1.hasNext());
        assertNotNull(page1.nextPageToken());

        PageResult<Song> page2 = songMetadataRepository.searchByTitle(
                prefix, PageRequest.of(0, 2), page1.nextPageToken());
        assertEquals(2, page2.content().size());
        assertTrue(page2.hasNext());
        assertNotNull(page2.nextPageToken());
        assertTrue(titles(page1).stream().noneMatch(titles(page2)::contains),
                "page 2 must not repeat page 1 items");

        PageResult<Song> page3 = songMetadataRepository.searchByTitle(
                prefix, PageRequest.of(0, 2), page2.nextPageToken());
        assertEquals(1, page3.content().size());
        assertFalse(page3.hasNext());
        assertEquals(null, page3.nextPageToken());

        assertEquals(5, titles(page1).size() + titles(page2).size() + titles(page3).size());
        assertEquals(
                5,
                Set.of(titles(page1), titles(page2), titles(page3)).stream()
                        .flatMap(Set::stream).distinct().count(),
                "walking the cursor must return each item exactly once"
        );
    }

    @Test
    void invalidCursorShouldFailWithIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                songMetadataRepository.searchByTitle("anything", PageRequest.of(0, 10), "not-a-valid-cursor"));
    }

    private void insertSong(String title) {
        songMetadataRepository.save(Song.create(title, AlbumId.generate(), "storage-" + UUID.randomUUID()));
    }

    private Set<String> titles(PageResult<Song> page) {
        return page.content().stream().map(Song::getTitle).collect(Collectors.toSet());
    }
}