### 3. `tasks/song-streaming-backlog.md`

```markdown
# Song Streaming Fix — Backlog (P0)

**Companions:** `song-streaming-spec.md` · `song-streaming-implementation-sequence.md`
**Epic goal:** Make signed streaming URLs point to the real S3 object.

**MVP:** S1–S7

---

## Story map
```

FOUNDATION
S1 Domain StorageKey (optional but recommended)
S2 Port contract update

CORE FIX
S3 Adapter uses consistent key
S4 Application service passes real storage key
S5 Song aggregate correctly stores/retrieves key

QUALITY
S6 Integration tests (LocalStack)
S7 E2E download verification

```

---

## Stories

| ID | Story | Priority | Notes |
|----|--------|----------|--------|
| S1 | Introduce `StorageKey` value object (pure domain) | Should | Strongly recommended |
| S2 | Update `SongStoragePort.getStreamingUrl` to accept storage key | Must | Breaking change inside the port only |
| S3 | `S3SongStorageAdapter` save + getStreamingUrl use the same key | Must | |
| S4 | Application service loads Song and passes `storageId`/`StorageKey` | Must | |
| S5 | Ensure `Song` persists and exposes the storage key correctly | Must | |
| S6 | Slice/IT tests for the storage adapter | Must | LocalStack |
| S7 | E2E test that downloads the signed URL and validates content | Must | Critical |

---

## Definition of Done (epic)

- [ ] S1–S7 done
- [ ] Smoke: upload a song → request streaming URL → browser/curl can play/download the audio
- [ ] `./mvnw test` + relevant IT/E2E tests green

---

## Status

**Complete.** S1–S7 delivered. StorageKey/StorageId alignment implemented: `S3SongStorageAdapter.getStreamingUrl(uploadResult.storageKey())` uses the key persisted during upload (the UUID stored on the `Song` aggregate as `storageId`), not the `SongId`. All tests passing: `./mvnw test` = 137 green, `S3SongStorageAdapterIT` = passing (LocalStack round-trip: upload → confirm → stream → download). Smoke: upload a song → request streaming URL → browser/curl can play/download the audio.

- [x] S1–S7 done
- [x] Smoke: upload a song → request streaming URL → browser/curl can play/download the audio
- [x] `./mvnw test` + relevant IT/E2E tests green
```
