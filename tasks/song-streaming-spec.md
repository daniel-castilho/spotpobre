### 2. `tasks/song-streaming-spec.md`

```markdown
# Song Streaming Fix — Technical Specification (P0)

**Status:** Draft for implementation (critical)
**Focus:** Align S3 object key used on upload with the key used to generate signed streaming URLs
**Companions:** `song-streaming-backlog.md` · `song-streaming-implementation-sequence.md`

---

## 1. Purpose & scope

Make song streaming functional.

**In scope (P0):**

- Ensure the key used in `S3SongStorageAdapter.saveSong` is the same key later used in `getStreamingUrl`
- Persist and retrieve the real storage key (`storageId`) on the `Song` aggregate
- Introduce a domain value object `StorageKey` (recommended)
- Update `SongStoragePort` so `getStreamingUrl` receives the storage key (not `SongId`)
- Make the application service load the song, extract the storage key, and pass it to the port
- E2E verification that the signed URL actually serves the audio file

**Out of scope:**

- Changing to presigned upload flow (that belongs to a later epic)
- CDN implementation
- Changing the public REST response shape (unless strictly necessary)
- Multipart / chunked upload

---

## 2. Root cause

Current (broken) flow:

1. `saveSong` generates a random key → stores file in S3 under that key → returns the key
2. `Song.create` stores that key as `storageId` and generates a separate `SongId` (UUID)
3. `getStreamingUrl(songId)` ignores `storageId` and uses `songId` as the S3 key
4. Result: signed URL points to a non-existent object

---

## 3. Target architecture
```

Upload:
Controller → UseCase → SongStoragePort.save(...) → returns StorageKey
→ Song is created/updated with that StorageKey

Streaming:
Controller → UseCase → load Song → extract StorageKey
→ SongStoragePort.getStreamingUrl(StorageKey) → signed URL

```

Domain stays pure. AWS details stay in the infrastructure adapter.

---

## 4. Domain changes

- Prefer introducing `StorageKey` value object (immutable, validated)
- `Song` already has (or must expose) the storage key
- `SongStoragePort` method signatures must use the storage key for retrieval/streaming

---

## 5. Testing requirements

- Unit tests for any new value object and for the application service
- Integration test of `S3SongStorageAdapter` (save + getStreamingUrl with real LocalStack)
- E2E test that:
  1. Uploads a small audio file
  2. Requests the streaming URL
  3. Performs an HTTP GET on the signed URL
  4. Asserts status 200, correct Content-Type and non-empty body

---

## 6. Definition of Done

- [ ] Upload and streaming use the same S3 key
- [ ] Signed URL is valid and serves the file
- [ ] E2E download test passes
- [ ] No regression on existing song metadata endpoints
- [ ] Documentation (CHANGELOG / AGENTS.md) updated if required
```
