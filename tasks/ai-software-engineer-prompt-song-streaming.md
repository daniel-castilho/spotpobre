### 1. `tasks/ai-software-engineer-prompt-song-streaming.md`

```markdown
# AI Software Engineer Prompt — Song Streaming Fix (P0)

**Status:** Not implemented — critical functional epic.
**Target:** Correct S3 key usage for song streaming
**Package:** `com.spotpobre.backend`

You implement the complete fix so that signed streaming URLs point to the actual object stored in S3.

---

## Sources of truth (read in order)

1. `AGENTS.md`
2. `docs/coding-standards.md` · `docs/testing-playbook.md` · `docs/lessons.md`
3. `tasks/song-streaming-spec.md` — what to build
4. `tasks/song-streaming-backlog.md` — stories
5. `tasks/song-streaming-implementation-sequence.md` — build order
6. Reference: `SongStoragePort`, `S3SongStorageAdapter`, `Song` domain model, existing upload flow

---

## Goal

When a client requests a song streaming URL, the signed URL must point to the **exact same S3 object key** that was used when the file was uploaded.
Currently the system saves the file under a random `storageId` but generates the signed URL using the `SongId`. Streaming is broken.

No new features. No change to the public API contract beyond making streaming actually work.

---

## Non-negotiable rules

- Domain and application remain free of AWS SDK types
- Prefer a pure domain `StorageKey` (or equivalent value object)
- `SongStoragePort.getStreamingUrl` must receive the real storage key, never the business `SongId`
- Existing upload flow must continue to work
- English only
- No new Maven dependencies without human approval
- Unit + integration + E2E tests required (E2E must actually download the object)
- Do not push unless the human asks

---

## Definition of Done (epic)

- [ ] Upload stores the file and persists the correct `storageId` / `StorageKey`
- [ ] `getStreamingUrl` uses the persisted storage key
- [ ] Signed URL returns HTTP 200 and the correct audio content
- [ ] E2E test downloads the object and validates content + Content-Type
- [ ] Existing tests remain green
- [ ] `./mvnw test` and relevant `*IT` tests pass

Start at **Step 0** of `song-streaming-implementation-sequence.md`. If scope is unclear, **stop and ask**.
```
