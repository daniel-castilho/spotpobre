package com.spotpobre.backend.infrastructure.web.controller;

import com.spotpobre.backend.application.artist.port.in.GrantArtistAccountUseCase;
import com.spotpobre.backend.application.artist.port.in.RevokeArtistAccountUseCase;
import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.user.model.Role;
import com.spotpobre.backend.infrastructure.web.dto.request.GrantArtistAccountRequest;
import com.spotpobre.backend.infrastructure.web.dto.response.ArtistAccountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin-only management of artist memberships (OWNER / MANAGER).
 */
@RestController
@RequestMapping("/api/v1/artists/{artistId}/accounts")
@RequiredArgsConstructor
@Tag(name = "Artist Accounts", description = "Admin-only artist membership management")
public class ArtistAccountController {

    private final GrantArtistAccountUseCase grantArtistAccountUseCase;
    private final RevokeArtistAccountUseCase revokeArtistAccountUseCase;

    @PostMapping
    @Operation(summary = "Grant a membership on an artist (admin only)")
    public ResponseEntity<ArtistAccountResponse> grant(
            @PathVariable final UUID artistId,
            @RequestBody @Valid final GrantArtistAccountRequest request,
            final Authentication authentication
    ) {
        final ArtistAccount account = grantArtistAccountUseCase.grant(
                new GrantArtistAccountUseCase.GrantArtistAccountCommand(
                        isAdmin(authentication),
                        new ArtistId(artistId),
                        request.userId(),
                        request.permission()
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(account));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Revoke a membership from an artist (admin only)")
    public ResponseEntity<Void> revoke(
            @PathVariable final UUID artistId,
            @PathVariable final UUID userId,
            final Authentication authentication
    ) {
        revokeArtistAccountUseCase.revoke(
                new RevokeArtistAccountUseCase.RevokeArtistAccountCommand(
                        isAdmin(authentication), new ArtistId(artistId), userId));
        return ResponseEntity.noContent().build();
    }

    private static boolean isAdmin(final Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + Role.ADMIN.name()).equals(authority.getAuthority()));
    }

    private static ArtistAccountResponse toResponse(final ArtistAccount account) {
        return new ArtistAccountResponse(
                account.artistId().value(),
                account.userId(),
                account.permission(),
                account.createdAt()
        );
    }
}
