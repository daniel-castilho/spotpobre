### 3. `tasks/data-consistency-backlog.md`

```markdown
# Data Consistency & Modelling — Backlog (P1)

**Companions:** `data-consistency-spec.md` · `data-consistency-implementation-sequence.md`
**Epic goal:** Eliminate aggregate drift and enforce business rules in the database.

**MVP:** S1–S9

---

## Story map
```

ANALYSIS & DECISION
S1 Decide single source of truth for Playlists and Album–Song

PLAYLISTS
S2 Enforce max 10 playlists persistently
S3 Remove or stop relying on embedded playlists in User

ALBUM / SONG
S4 Make Album–Song relationship consistent

WRITE SAFETY
S5 Conditional write for user registration (email uniqueness)
S6 Conditional / versioned updates for playlists
S7 Basic compensation or explicit failure handling for S3 + DynamoDB

QUALITY
S8 Integration tests for invariants and concurrency
S9 Documentation of the data model decisions

```

---

## Stories

| ID | Story | Priority | Notes |
|----|--------|----------|--------|
| S1 | Document and implement the chosen single source of truth | Must | Architectural decision |
| S2 | Persistently enforce MAX_PLAYLISTS_PER_USER = 10 | Must | |
| S3 | Eliminate drift between User embedded playlists and Playlists table | Must | |
| S4 | Keep Album and its songs consistent (or remove embedded list) | Must | |
| S5 | User registration uses conditional put on email | Must | |
| S6 | Playlist mutations use condition/version to avoid lost updates | Must | |
| S7 | Handle partial failure between S3 upload and metadata save | Should | Minimum viable |
| S8 | ITs covering limit, uniqueness and basic concurrency | Must | |
| S9 | Update docs / AGENTS.md / CHANGELOG with modelling decisions | Must | |

---

## Definition of Done (epic)

- [x] S1–S9 done
- [x] 11th playlist is rejected
- [x] Duplicate email cannot be created
- [x] No silent drift between aggregates
- [x] Relevant tests green

---

## Status

**Done.** Shipped on `main` as part of the Unreleased work (see `CHANGELOG.md`). All stories S1–S9
implemented; decisions recorded in `docs/data-model-decisions.md`; unit tests (130) and the full
`*IT` suite (13) pass.
```
