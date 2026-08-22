package com.spotpobre.backend.domain.idempotency.model;

/**
 * Lifecycle state of an idempotency record.
 *
 * <ul>
 *   <li>{@code IN_PROGRESS} — claimed; operation running or interrupted (lease governs takeover)</li>
 *   <li>{@code COMPLETED} — result snapshot stored; replays return it</li>
 *   <li>{@code FAILED_FINAL} — deterministic post-claim 4xx; replayed deterministically</li>
 * </ul>
 */
public enum IdempotencyState {
    IN_PROGRESS,
    COMPLETED,
    FAILED_FINAL
}
