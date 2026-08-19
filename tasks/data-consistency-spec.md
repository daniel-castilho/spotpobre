### 2. `tasks/data-consistency-spec.md`

```markdown
# Data Consistency & Modelling — Technical Specification (P1)

**Status:** Draft for implementation (high priority)
**Focus:** Single source of truth, enforced limits, safer concurrent writes
**Companions:** `data-consistency-backlog.md` · `data-consistency-implementation-sequence.md`

---

## 1. Purpose & scope

Make the persisted state match the domain rules and eliminate drift.

**In scope (P1):**

- Enforce `MAX_PLAYLISTS_PER_USER = 10` persistently
- Resolve duplicated playlist modelling (embedded in User + independent table)
- Resolve `Album.songs` drift
- Introduce conditional writes for critical operations (user registration by email, playlist updates, etc.)
- Design clear ownership of relationships in the DynamoDB single-table (or multi-table) design
- Compensation or explicit handling when S3 succeeds and DynamoDB fails (or vice-versa) — minimum viable safety

**Out of scope:**

- Full event-sourcing or CQRS rewrite
- Changing to a relational database
- Implementing the later Presigned-URL upload epic
- Complex multi-item transactions beyond what is required for the invariants above

---

## 2. Current problems (summary)

| Problem                                                    | Consequence                      |
| ---------------------------------------------------------- | -------------------------------- |
| `User.createPlaylist()` checks limit but User is not saved | Limit never reached in practice  |
| Playlists stored in two places                             | Drift, inconsistent reads        |
| `Album.songs` not updated on song upload                   | Aggregate and database diverge   |
| `findByEmail` + `putItem` without condition                | Duplicate users possible         |
| Read-modify-write on playlists without version/condition   | Lost updates under concurrency   |
| `@Transactional` on services                               | False sense of atomicity with S3 |

---

## 3. Target design principles

1. **Single source of truth** per relationship.
   - Recommended direction: keep Playlists and Songs as independent items; query by GSI / owner; remove or stop using embedded collections for authoritative data.
2. **Invariants enforced at write time** with conditional expressions.
3. **Domain remains pure** — persistence concerns stay in adapters.
4. Clear decision recorded in code/comments about which collection (if any) is authoritative.

---

## 4. Expected changes

- Domain: strengthen invariants (or move checks to application with persistent counters)
- Application services: load current state, check limits, then write with conditions
- Persistence adapters: use condition expressions (`attribute_not_exists`, version checks, etc.)
- Possible schema/GSI adjustments (documented)
- Tests for the limit, uniqueness and concurrent scenarios

---

## 5. Testing requirements

- Unit tests for domain/application invariants
- Integration tests proving:
  - 11th playlist is rejected
  - Duplicate email registration is rejected
  - Concurrent playlist updates do not silently lose data (or behave as designed)
- Regression tests for existing happy paths

---

## 6. Definition of Done

- [x] Playlist limit reliably enforced
- [x] No divergent sources of truth for playlists and album songs
- [x] Critical writes are conditional
- [x] Tests prove the new guarantees
- [x] Documentation updated (CHANGELOG, AGENTS.md, data-model notes)

---

## 7. Status

**Done.** Shipped on `main` as part of the Unreleased work (see `CHANGELOG.md`); decisions in
`docs/data-model-decisions.md`; unit tests (130) and the full `*IT` suite (13) pass.
```
