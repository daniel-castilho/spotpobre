### 3. `tasks/playlist-security-backlog.md`

```markdown
# Playlist Security — Backlog (P0)

**Companions:** `playlist-security-spec.md` · `playlist-security-implementation-sequence.md`
**Epic goal:** Eliminate IDOR on playlist mutations by enforcing ownership.

**MVP:** S1–S7

---

## Story map
```

FOUNDATION
S1 Typed exception + command updates
S2 Application service ownership checks

MUTATIONS
S3 Update playlist – ownership
S4 Delete playlist – ownership
S5 Add song – ownership
S6 Remove song – ownership

QUALITY
S7 E2E A-versus-B + exception mapping

```

---

## Stories

| ID | Story | Priority | Notes |
|----|--------|----------|--------|
| S1 | Introduce/reuse Forbidden exception + add `currentUserId` to mutation commands | Must | Keep domain clean |
| S2 | Central ownership guard helper or method on services | Must | Avoid copy-paste |
| S3 | UpdatePlaylistDetailsService enforces owner | Must | |
| S4 | DeletePlaylistService enforces owner | Must | |
| S5 | AddSongToPlaylistService enforces owner | Must | |
| S6 | RemoveSongFromPlaylistService enforces owner | Must | |
| S7 | E2E tests (User A vs User B) + 403 mapping in GlobalExceptionHandler | Must | Critical |

---

## Definition of Done (epic)

- [ ] S1–S7 done
- [ ] Smoke: User A mutates own playlist successfully; User B receives 403 on all four operations
- [ ] `./mvnw test` + relevant IT tests green

---

## Status

**Not started.** Critical P0.
```
