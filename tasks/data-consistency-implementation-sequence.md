### 4. `tasks/data-consistency-implementation-sequence.md`

```markdown
# Data Consistency & Modelling — Implementation Sequence (P1)

**Companions:** `data-consistency-spec.md` · `data-consistency-backlog.md`
**Rule:** Finish each step’s “Done when” before the next. Do not invent scope.

---

## Step 0 — Analysis & decision

1. Map current write paths for:
   - Create playlist
   - Add/remove song from playlist
   - Upload song / create song under album
   - User registration
2. Identify every place that reads the embedded collections vs the independent tables
3. Decide (and write down) the single source of truth for:
   - User ↔ Playlists
   - Album ↔ Songs

**Done when:** A short design decision record exists and is agreed.

---

## Step 1 — Playlist limit enforcement

- Change `CreatePlaylistService` (and related code) so the limit check is based on persistent state.
- Options (choose the one aligned with the decision in Step 0):
  - Count existing playlists via query/GSI and reject if ≥ 10
  - Maintain a counter attribute with conditional increment
- Reject the 11th playlist with a clear domain/application exception

**Done when:** Unit + IT prove that the 11th playlist is rejected and the 10th succeeds.

---

## Step 2 — Remove playlist drift

- Stop treating the embedded collection inside `User` as authoritative (or remove it).
- Ensure all reads and writes go through the chosen source of truth.
- Migrate or clean any code that still updates the embedded list.

**Done when:** No code path leaves the two representations out of sync.

---

## Step 3 — Album–Song consistency

- Apply the same single-source-of-truth decision to `Album.songs`.
- On song creation, either:
  - Update the album in the same logical operation (with condition), or
  - Stop maintaining the embedded list and always query songs by albumId.

**Done when:** After uploading a song, the system’s view of “songs of this album” is correct.

---

## Step 4 — Conditional writes

- User registration: use condition `attribute_not_exists` on the email (or the unique key).
- Playlist updates: add version attribute or equivalent condition to prevent silent lost updates.
- Apply the same pattern to other critical write paths identified in Step 0.

**Done when:** Integration tests demonstrate that duplicates and lost updates are prevented.

---

## Step 5 — S3 + DynamoDB partial failure

- Minimum viable handling:
  - If metadata save fails after S3 upload, log + optionally attempt delete of the orphan object, or
  - Make the order and error handling explicit so operators can detect orphans.
- Document the chosen strategy.

**Done when:** Behaviour on partial failure is deterministic and tested or clearly documented.

---

## Step 6 — Tests & verification

- Add/extend ITs for:
  - Playlist limit
  - Email uniqueness
  - Concurrent playlist modification (basic)
  - Album–Song consistency after upload
- Run full relevant suite.

**Done when:** All new and existing tests pass.

---

## Step 7 — Documentation

- Update CHANGELOG, AGENTS.md and any data-model notes with the decisions taken.
- Record the single source of truth clearly.

**Done when:** Future developers can understand the model without reading the old analysis.

---

## Smoke path

1. Create 10 playlists for a user → success
2. Attempt 11th → rejected with clear error
3. Register two users with the same email → second fails
4. Upload song to album → album’s song list (or query) reflects the new song
5. Concurrent updates to the same playlist do not silently overwrite each other

---

_Pre-implementation sequence. After delivery, replace with an as-built status note._

---

## As-built status (post-delivery)

All steps 0–7 shipped on `main`:

- **Step 0/7 — decisions:** recorded in `docs/data-model-decisions.md` (single sources: `Playlists`
  table via `ownerId-index`; `Songs` table via `albumId-index`; embedded collections removed from
  `User` and `Album`; email uniqueness via `UserEmails` + `TransactWriteItems`; playlist optimistic
  locking via `version`; S3 orphan handling via `SongStoragePort.abortUpload`).
- **Step 1 — limit:** `PlaylistRepository.countByOwnerId` + `CreatePlaylistService` rejects the
  11th playlist (`IllegalStateException`); verified by `PlaylistLimitAndConcurrencyIT`.
- **Step 2 — drift removed:** `User.playlists` gone from domain, document, schema and mapper.
- **Step 3 — album songs:** `Album.songs` removed; `SongMetadataRepository.findByAlbumId` reads the
  `Songs` GSI; verified by `AlbumSongConsistencyIT`.
- **Step 4 — conditional writes:** `DynamoDbUserRepositoryImpl.registerNew`
  (`attribute_not_exists` in `TransactWriteItems`, retry on `TransactionConflict`); playlist
  `create`/`update` conditional puts throwing `PlaylistConcurrentModificationException`.
- **Step 5 — partial failure:** `InitiateSongUploadService` aborts the multipart upload when
  metadata persistence fails; `@Transactional` removed from upload services.
- **Step 6 — tests:** `./mvnw test` (130 unit) and `./mvnw test -Dtest='*IT'` (13) pass.
- **Smoke path 1–5:** all covered by the IT suite above.
```
