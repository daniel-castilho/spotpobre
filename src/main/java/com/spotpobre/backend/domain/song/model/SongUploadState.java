package com.spotpobre.backend.domain.song.model;

/**
 * Lifecycle states of a durable song upload (spec §7).
 *
 * <pre>
 * PENDING_UPLOAD ──► COMPLETING ──► COMPLETED
 *       │  ▲              │
 *       │  └──(released)──┘
 *       ▼                 ▼
 *    ABORTED ◄────────────┘
 * </pre>
 *
 * COMPLETED and ABORTED are terminal. Illegal transitions throw
 * {@link IllegalStateException} at the entity boundary.
 */
public enum SongUploadState {

    PENDING_UPLOAD,
    COMPLETING,
    COMPLETED,
    ABORTED;

    private static final boolean[][] LEGAL = {
            // from PENDING_UPLOAD -> {PENDING_UPLOAD, COMPLETING, COMPLETED, ABORTED}
            {true, true, false, true},
            // from COMPLETING -> {PENDING_UPLOAD (lease released after failure), COMPLETING, COMPLETED, ABORTED}
            {true, true, true, true},
            // from COMPLETED -> terminal
            {false, false, true, false},
            // from ABORTED -> terminal
            {false, false, false, true},
    };

    public boolean canTransitionTo(final SongUploadState target) {
        return LEGAL[this.ordinal()][target.ordinal()];
    }
}
