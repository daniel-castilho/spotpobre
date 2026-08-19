package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.AbstractIntegrationTest;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ArtistSearchPaginationIT extends AbstractIntegrationTest {

    @Autowired
    private ArtistRepository artistRepository;

    @Test
    void searchShouldBeCaseInsensitiveAndReturnMixedCaseMatches() {
        String suffix = "zz" + UUID.randomUUID().toString().substring(0, 8);
        insertArtist(suffix + " beatles the");
        insertArtist(suffix + " Beatles cover band");
        insertArtist(suffix + " BEATLES");

        PageResult<Artist> lower = artistRepository.searchByName(
                suffix + " beatles", PageRequest.of(0, 10), null);
        PageResult<Artist> upper = artistRepository.searchByName(
                suffix.toUpperCase() + " BEATLES", PageRequest.of(0, 10), null);
        PageResult<Artist> mixed = artistRepository.searchByName(
                suffix + " BeAtLeS", PageRequest.of(0, 10), null);

        assertEquals(3, lower.content().size());
        assertEquals(3, upper.content().size());
        assertEquals(3, mixed.content().size());
        assertEquals(names(lower), names(upper), "query casing must not change results");
        assertEquals(names(upper), names(mixed), "query casing must not change results");
    }

    @Test
    void paginationShouldReturnDifferentItemsAndWalkCursorToTheEnd() {
        String prefix = "artistwalk-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 1; i <= 4; i++) {
            insertArtist(prefix + "-" + i);
        }

        PageResult<Artist> page1 = artistRepository.searchByName(prefix, PageRequest.of(0, 3), null);
        assertEquals(3, page1.content().size());
        assertTrue(page1.hasNext());
        assertNotNull(page1.nextPageToken());

        PageResult<Artist> page2 = artistRepository.searchByName(
                prefix, PageRequest.of(0, 3), page1.nextPageToken());
        assertEquals(1, page2.content().size());
        assertFalse(page2.hasNext());
        assertEquals(null, page2.nextPageToken());
        assertTrue(names(page1).stream().noneMatch(names(page2)::contains),
                "page 2 must not repeat page 1 items");
    }

    @Test
    void invalidCursorShouldFailWithIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                artistRepository.searchByName("anything", PageRequest.of(0, 10), "not-a-valid-cursor"));
    }

    private void insertArtist(String name) {
        artistRepository.save(Artist.create(name));
    }

    private Set<String> names(PageResult<Artist> page) {
        return page.content().stream().map(Artist::getName).collect(Collectors.toSet());
    }
}