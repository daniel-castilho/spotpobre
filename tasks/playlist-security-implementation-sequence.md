### 4. `tasks/playlist-security-implementation-sequence.md`

```markdown
# Playlist Security — Implementation Sequence (P0)

**Companions:** `playlist-security-spec.md` · `playlist-security-backlog.md`
**Rule:** Finish each step’s “Done when” before the next. Do not invent scope.

---

## Step 0 — Analysis & preparation

1. Locate all playlist mutation services and their commands/use-case interfaces
2. Confirm how the authenticated user is currently obtained in controllers
3. Check existing exception hierarchy and GlobalExceptionHandler

**Done when:** Clear list of files to change exists.

---

## Step 1 — Exception + command contracts

- Create or reuse `ForbiddenException` / `PlaylistAccessDeniedException`
- Add `UserId currentUserId` (or equivalent) to all mutation commands / use-case inputs
- Update method signatures in application ports

**Done when:** Code compiles; no behavior change yet.

---

## Step 2 — Ownership guard

Implement a clear ownership check (private method or small helper) used by all mutation services:

```java
if (!playlist.getOwnerId().equals(currentUserId)) {
    throw new ForbiddenException("...");
}
```

**Done when:** Guard exists and is unit-tested in isolation if extracted.

---

## Step 3 — Wire services

Apply the guard in:

- UpdatePlaylistDetailsService
- DeletePlaylistService
- AddSongToPlaylistService
- RemoveSongFromPlaylistService

Update unit tests for each service (success + forbidden cases).

**Done when:** All four services enforce ownership; unit tests green.

---

## Step 4 — Controllers

- Controllers extract the authenticated `UserId` from the security context
- Pass it into the commands/use cases
- Do **not** trust any userId coming from the request body

**Done when:** Controllers compile and pass the real authenticated user.

---

## Step 5 — Exception → HTTP mapping

Ensure `GlobalExceptionHandler` (or equivalent) maps the new exception to **HTTP 403** with the standard error envelope.

**Done when:** 403 response is consistent with the rest of the API.

---

## Step 6 — E2E / Integration tests (A-versus-B)

Write tests that:

1. User A creates a playlist
2. User B attempts update / delete / add song / remove song
3. All four attempts return 403
4. User A can still perform the same operations successfully

**Done when:** E2E tests green.

---

## Step 7 — Final verification

- Run full relevant test suite
- Manual smoke (optional but recommended)
- Update CHANGELOG / AGENTS.md if the project rules require it

**Done when:** `./mvnw test` (and IT profile) succeeds and Definition of Done is met.

---

## Smoke path

1. Login as User A → create playlist → mutate it successfully
2. Login as User B → try to mutate User A’s playlist → receive 403 on every operation
3. Confirm User A can still manage their own playlist

---

_Pre-implementation sequence. After delivery, replace with an as-built status note._
```
