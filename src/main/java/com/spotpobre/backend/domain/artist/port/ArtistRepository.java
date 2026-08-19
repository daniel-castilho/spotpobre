package com.spotpobre.backend.domain.artist.port;

import com.spotpobre.backend.domain.artist.model.Artist;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.common.pagination.PageRequest;
import com.spotpobre.backend.domain.common.pagination.PageResult;

import java.util.Optional;

public interface ArtistRepository {
    Optional<Artist> findById(final ArtistId id);
    void save(final Artist artist);
    PageResult<Artist> searchByName(final String nameQuery, final PageRequest pageRequest, final String exclusiveStartKey);
}
