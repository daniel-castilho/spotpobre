package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.AbstractIntegrationTest;
import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.model.ArtistPermission;
import com.spotpobre.backend.domain.artist.port.ArtistAccountRepository;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DynamoDbArtistAccountRepositoryAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private ArtistAccountRepository artistAccountRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Test
    void createWithOwnerPersistsArtistAndMembershipAtomically() {
        Artist artist = Artist.create("Atomic Owner IT " + UUID.randomUUID());
        UUID ownerUserId = UUID.randomUUID();

        artistRepository.createWithOwner(artist, ArtistAccount.owner(artist.getId(), ownerUserId, Instant.now()));

        assertTrue(artistRepository.findById(artist.getId()).isPresent());
        Optional<ArtistAccount> account = artistAccountRepository.find(artist.getId(), ownerUserId);
        assertTrue(account.isPresent());
        assertEquals(ownerUserId, account.get().userId());
    }

    @Test
    void saveFindAndDeleteRoundTrip() {
        ArtistId artistId = ArtistId.generate();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        artistAccountRepository.save(ArtistAccount.manager(artistId, userId, createdAt));

        Optional<ArtistAccount> found = artistAccountRepository.find(artistId, userId);
        assertTrue(found.isPresent());
        assertEquals(ArtistPermission.MANAGER, found.get().permission());
        assertEquals(createdAt, found.get().createdAt());

        List<ArtistAccount> all = artistAccountRepository.findByArtistId(artistId);
        assertEquals(1, all.size());

        assertTrue(artistAccountRepository.delete(artistId, userId));
        assertFalse(artistAccountRepository.delete(artistId, userId));
        assertTrue(artistAccountRepository.findByArtistId(artistId).isEmpty());
    }

    @Test
    void findByArtistIdReturnsOnlyThatArtistsAccounts() {
        ArtistId artistA = ArtistId.generate();
        ArtistId artistB = ArtistId.generate();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        Instant now = Instant.now();

        artistAccountRepository.save(ArtistAccount.owner(artistA, user1, now));
        artistAccountRepository.save(ArtistAccount.manager(artistA, user2, now));
        artistAccountRepository.save(ArtistAccount.owner(artistB, user1, now));

        List<ArtistAccount> accountsA = artistAccountRepository.findByArtistId(artistA);
        assertEquals(2, accountsA.size());
        assertTrue(accountsA.stream().allMatch(a -> a.artistId().equals(artistA)));
    }
}
