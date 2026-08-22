package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.RevokeArtistAccountUseCase;
import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.model.ArtistPermission;
import com.spotpobre.backend.domain.artist.port.ArtistAccountRepository;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.common.NotFoundException;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class RevokeArtistAccountService implements RevokeArtistAccountUseCase {

    private final ArtistAccountRepository artistAccountRepository;

    @Override
    public void revoke(final RevokeArtistAccountCommand command) {
        if (!command.actorIsAdmin()) {
            throw new ForbiddenException("Only administrators can manage artist accounts");
        }
        final ArtistId artistId = command.artistId();
        final UUID targetUserId = command.targetUserId();
        if (artistAccountRepository.find(artistId, targetUserId).isEmpty()) {
            throw new NotFoundException("Artist account not found for user " + targetUserId);
        }

        // Safety rule: an artist must always keep at least one OWNER.
        if (isLastOwner(artistId, targetUserId)) {
            throw new IllegalStateException("Cannot revoke the last OWNER of artist " + artistId);
        }
        artistAccountRepository.delete(artistId, targetUserId);
    }

    private boolean isLastOwner(final ArtistId artistId, final UUID targetUserId) {
        final List<ArtistAccount> accounts = artistAccountRepository.findByArtistId(artistId);
        final boolean targetIsOwner = accounts.stream().anyMatch(account ->
                account.userId().equals(targetUserId) && account.permission() == ArtistPermission.OWNER);
        if (!targetIsOwner) {
            return false;
        }
        return accounts.stream()
                .filter(account -> account.permission() == ArtistPermission.OWNER)
                .count() == 1;
    }
}
