package com.spotpobre.backend.domain.artist.model;

/**
 * Permission level of an {@link ArtistAccount} membership.
 */
public enum ArtistPermission {
    /** Full control of the artist resource, including account management. */
    OWNER,
    /** Can manage the artist's catalogue (albums, songs) but not accounts. */
    MANAGER
}
