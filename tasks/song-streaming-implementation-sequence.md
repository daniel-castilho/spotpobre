### 4. `tasks/song-streaming-implementation-sequence.md`

```markdown
# Song Streaming Fix — Implementation Sequence (P0)

**Companions:** `song-streaming-spec.md` · `song-streaming-backlog.md`
**Rule:** Finish each step’s “Done when” before the next. Do not invent scope.

---

## Step 0 — Analysis

1. Read `S3SongStorageAdapter.saveSong` and `getStreamingUrl`
2. Read how `Song` is created and how `storageId` is stored
3. Identify every call site of `getStreamingUrl`
4. Confirm current E2E coverage (it currently does **not** download the file)

**Done when:** Root cause and all touch-points are clearly listed.

---

## Step 1 — Domain value object (recommended)

Create pure domain `StorageKey` (or reuse/strengthen existing type).

- Immutable
- Validation (non-blank, etc.)
- No framework imports

**Done when:** Value object exists and has unit tests.

---

## Step 2 — Port contract

Change `SongStoragePort`:

- `getStreamingUrl` must receive the storage key (not `SongId`)
- Adjust any related methods if necessary
- Keep domain/application free of AWS types

**Done when:** Port compiles; call sites show compilation errors (expected).

---

## Step 3 — Adapter implementation

In `S3SongStorageAdapter`:

- `saveSong` continues to generate (or receive) a key and returns it
- `getStreamingUrl` uses exactly the key it receives
- No more usage of `SongId` as S3 object key

**Done when:** Adapter compiles and unit/slice tests can be written.

---

## Step 4 — Application layer

- Load the `Song`
- Extract its storage key
- Pass the storage key to `SongStoragePort.getStreamingUrl`
- Update any DTOs or response mapping only if required

**Done when:** Application service unit tests green (success path + song not found).

---

## Step 5 — Persistence / Song model

Ensure the `storageId` / `StorageKey` is:

- Correctly saved when the song is created
- Correctly loaded when the song is retrieved

**Done when:** Round-trip (save → load → get key) works in tests.

---

## Step 6 — Integration tests

Write or extend tests that:

- Upload a small file via the adapter (LocalStack)
- Request a streaming URL using the returned key
- Optionally perform a GET against LocalStack and assert status/content

**Done when:** IT tests green.

---

## Step 7 — E2E verification

Create/update E2E test:

1. Authenticated artist uploads a song (small test fixture)
2. Client requests the song details / streaming URL
3. Test performs HTTP GET on the signed URL
4. Asserts 200, correct Content-Type, and body length > 0

**Done when:** E2E test passes reliably.

---

## Step 8 — Final checks

- Full relevant test suite green
- Manual smoke (upload → play URL)
- Update CHANGELOG / AGENTS.md if project rules require it

**Done when:** Definition of Done is fully met.

---

## Smoke path

1. Login as artist
2. Upload a small mp3/wav
3. Request song details → obtain streaming URL
4. Open the URL (curl or browser) → audio is returned with 200
5. Confirm the object key in S3 matches the key used in the signed URL

---

## As-built status (delivered in commit `c0b940f`)
```
