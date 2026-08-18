### 2. `tasks/playlist-security-spec.md`

```markdown
# Playlist Security — Technical Specification (P0)

**Status:** Draft for implementation (critical)
**Focus:** Ownership authorization on playlist mutations
**Companions:** `playlist-security-backlog.md` · `playlist-security-implementation-sequence.md`

---

## 1. Purpose & scope

Prevent IDOR (Insecure Direct Object Reference) on playlists.

**In scope (MVP / P0):**

- Enforce ownership on:
  - Update playlist details (name, etc.)
  - Delete playlist
  - Add song to playlist
  - Remove song from playlist
- `currentUserId` must be injected into every mutation command/use case
- Clear `Forbidden` / authorization exception when owner does not match
- Correct HTTP 403 mapping

**Out of scope:**

- Changing read endpoints (list my playlists, get by id for public/owner)
- Sharing / collaborative playlists
- Admin override (unless already present)
- Rate limiting or additional security features

---

## 2. Architecture impact
```

Controller (extracts authenticated UserId)
↓
Application command / UseCase (now carries currentUserId)
↓
Application Service
↓
Load Playlist → check playlist.ownerId.equals(currentUserId)
↓
If not owner → throw ForbiddenException (or domain equivalent)
↓
Proceed with business logic

```

Ownership check **must** live in the application layer (or domain if modeled as a method on Playlist).

---

## 3. Domain / Application changes

- Mutation commands / use-case inputs must include `UserId currentUserId`
- `Playlist` already has `ownerId` — reuse it
- Introduce or reuse a typed exception: `ForbiddenException`, `PlaylistAccessDeniedException`, etc.
- Do **not** put Spring Security types inside domain

---

## 4. Affected components (expected)

- `UpdatePlaylistDetailsService` (+ command)
- `DeletePlaylistService`
- `AddSongToPlaylistService`
- `RemoveSongFromPlaylistService`
- Corresponding controllers (pass authenticated user)
- `GlobalExceptionHandler` (map to 403)
- SecurityConfig (already requires authenticated — keep it)

---

## 5. Testing requirements

- Unit tests: service rejects when `ownerId != currentUserId`
- Unit tests: service succeeds when owner matches
- E2E / IT: User A creates playlist → User B tries every mutation → expects 403
- Existing happy-path tests must continue to pass (they already act as owner)

---

## 6. Definition of Done

- [ ] All four mutation paths enforce ownership
- [ ] 403 returned with consistent error envelope
- [ ] E2E A-versus-B coverage
- [ ] No regression on legitimate owner operations
- [ ] AGENTS.md / CHANGELOG updated if required by project rules
```
