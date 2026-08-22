package com.spotpobre.backend.domain.artist.port;

import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for artist membership persistence.
 */
public interface ArtistAccountRepository {

    void save(ArtistAccount account);

    Optional<ArtistAccount> find(ArtistId artistId, UUID userId);

    List<ArtistAccount> findByArtistId(ArtistId artistId);

    /**
     * @return true when the membership existed and was removed
     */
    boolean delete(ArtistId artistId, UUID userId);
}
