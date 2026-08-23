package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.AbstractIntegrationTest;
import com.spotpobre.backend.application.album.port.in.ListAlbumsByArtistUseCase;
import com.spotpobre.backend.application.artist.port.in.ListArtistsUseCase;
import com.spotpobre.backend.domain.album.model.Album;
import com.spotpobre.backend.domain.album.model.AlbumId;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cursor pagination for the catalog listing endpoints added on the roadmap item
 * "pagination on artists/albums": artists via storage-native scan, albums of one artist via the
 * {@code artistId-index} GSI. Pins the invariants the web layer relies on: no lost rows, no
 * repeated rows, correct {@code hasNext}/{@code nextPageToken} handoff and 404 semantics for an
 * unknown artist.
 */
@SpringBootTest
class CatalogPaginationIT extends AbstractIntegrationTest {

    private static final String ARTIST_NAME_PREFIX = "catalog-it-";

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private ListArtistsUseCase listArtistsUseCase;

    @Autowired
    private ListAlbumsByArtistUseCase listAlbumsByArtistUseCase;

    @Test
    void artistListing_paginatesWithoutLossOrRepetition() {
        final int seeded = 7;
        Set<String> seededNames = new HashSet<>();
        for (int i = 0; i < seeded; i++) {
            String name = ARTIST_NAME_PREFIX + UUID.randomUUID();
            artistRepository.save(Artist.create(name));
            seededNames.add(name);
        }

        // Walk pages of 3 until exhaustion, collecting every row seen.
        List<Artist> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            PageResult<Artist> page = listArtistsUseCase.listArtists(
                    new ListArtistsUseCase.ListArtistsCommand(PageRequest.of(0, 3), cursor));
            seen.addAll(page.content());
            assertTrue(page.content().size() <= 3, "page size must be respected");
            cursor = page.nextPageToken();
            assertTrue(cursor == null || page.hasNext());
            assertTrue(pages++ < 100, "pagination must terminate");
        } while (cursor != null);

        // No loss, no repetition: every seeded artist appears exactly once across all pages.
        List<String> seenNames = seen.stream().map(Artist::getName).filter(seededNames::contains).toList();
        assertEquals(seeded, seenNames.size(), "every seeded artist must appear exactly once");
        assertEquals(seededNames.size(), new HashSet<>(seenNames).size(), "no artist may repeat across pages");
    }

    @Test
    void albumListing_paginatesByArtistAndAnswersEmptyForKnownArtist() {
        Artist artist = Artist.create("albums-" + UUID.randomUUID());
        artistRepository.save(artist);

        for (int i = 0; i < 5; i++) {
            albumRepository.save(Album.builder()
                    .id(AlbumId.generate())
                    .artistId(artist.getId())
                    .name("Album " + i)
                    .build());
        }

        List<String> names = new ArrayList<>();
        String cursor = null;
        do {
            PageResult<Album> page = listAlbumsByArtistUseCase.listAlbumsByArtist(
                    new ListAlbumsByArtistUseCase.ListAlbumsByArtistCommand(
                            artist.getId(), PageRequest.of(0, 2), cursor));
            page.content().forEach(album -> names.add(album.getName()));
            cursor = page.nextPageToken();
        } while (cursor != null);

        assertEquals(5, names.size(), "all albums must be reachable through cursored pages");
        assertEquals(5, new HashSet<>(names).size(), "no album may repeat across pages");
        assertTrue(names.stream().allMatch(n -> n.startsWith("Album ")));
    }

    @Test
    void albumListing_knownArtistWithoutAlbums_answersEmptyPage() {
        Artist artist = Artist.create("empty-" + UUID.randomUUID());
        artistRepository.save(artist);

        PageResult<Album> page = listAlbumsByArtistUseCase.listAlbumsByArtist(
                new ListAlbumsByArtistUseCase.ListAlbumsByArtistCommand(
                        artist.getId(), PageRequest.of(0, 20), null));

        assertTrue(page.content().isEmpty());
        assertFalse(page.hasNext());
    }

    @Test
    void albumListing_unknownArtist_answersNotFound() {
        assertThrows(NotFoundException.class,
                () -> listAlbumsByArtistUseCase.listAlbumsByArtist(
                        new ListAlbumsByArtistUseCase.ListAlbumsByArtistCommand(
                                new ArtistId(UUID.randomUUID()), PageRequest.of(0, 20), null)));
    }
}
