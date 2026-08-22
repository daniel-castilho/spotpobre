package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.RequireArtistAccessUseCase;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.port.ArtistAccountRepository;
import com.spotpobre.backend.domain.common.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class ArtistAccessService implements RequireArtistAccessUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ArtistAccessService.class);

    private final ArtistAccountRepository artistAccountRepository;

    @Override
    public void requireAccess(final ActorArtistRef actor, final ArtistId artistId) {
        if (actor.isAdmin()) {
            logger.info("artist_access admin_override artistId={} actorUserId={}",
                    artistId.value(), actor.userId());
            return;
        }
        artistAccountRepository.find(artistId, actor.userId())
                .orElseThrow(() -> {
                    logger.warn("artist_access denied artistId={} actorUserId={}",
                            artistId.value(), actor.userId());
                    return new ForbiddenException(
                            "User " + actor.userId() + " has no membership on artist " + artistId);
                });
    }
}
