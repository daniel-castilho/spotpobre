package com.spotpobre.backend.domain.idempotency.model;

/**
 * Kind of resource an idempotent operation reserves. The stable preassigned resource ID is
 * stored in the claim so crash recovery can reuse (never duplicate) it.
 */
public enum IdempotencyResourceType {
    USER,
    ARTIST,
    ALBUM,
    PLAYLIST,
    SONG_UPLOAD
}
