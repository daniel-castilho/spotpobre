### 1. `tasks/ai-software-engineer-prompt-data-consistency.md`

```markdown
# AI Software Engineer Prompt — Data Consistency & Modelling (P1)

**Status:** Not implemented — high-priority data integrity epic.
**Target:** Single source of truth for aggregates + enforced business rules + safer writes
**Package:** `com.spotpobre.backend`

You implement the complete fix for duplicated aggregates, unenforced business rules and missing concurrency control in the DynamoDB model.

---

## Sources of truth (read in order)

1. `AGENTS.md`
2. `docs/coding-standards.md` · `docs/testing-playbook.md` · `docs/lessons.md`
3. `tasks/data-consistency-spec.md` — what to build
4. `tasks/data-consistency-backlog.md` — stories
5. `tasks/data-consistency-implementation-sequence.md` — build order
6. Reference: `User`, `Playlist`, `Album`, `Song` domain models, corresponding Documents and repository adapters

---

## Goal

Eliminate data drift between aggregates and enforce business invariants that currently exist only in memory.

Main problems today:

- Playlist limit (max 10 per user) is checked but never persisted → rule is bypassable.
- Playlists exist both embedded inside `User` and as independent items in the Playlists table.
- `Album.songs` collection is not kept in sync when songs are uploaded.
- No conditional writes → possible duplicate users (same email) and lost updates under concurrency.
- `@Transactional` gives a false sense of atomicity between S3 and DynamoDB.

---

## Non-negotiable rules

- Choose **one** source of truth for each relationship (prefer independent entities + GSI over embedded collections for this DynamoDB design)
- Domain invariants must be enforceable and persisted
- Prefer conditional expressions / optimistic locking / `TransactWriteItems` where appropriate
- Domain and application stay free of AWS SDK types
- English only
- No new Maven dependencies without human approval
- Existing public API behaviour should remain compatible unless a breaking change is explicitly approved
- Do not push unless the human asks

---

## Definition of Done (epic)

- [ ] Playlist limit of 10 is reliably enforced
- [ ] No more divergent embedded vs independent collections for playlists
- [ ] Album–Song relationship has a single source of truth
- [ ] Critical writes use conditional expressions or transactions where needed
- [ ] Unit + integration tests cover the new invariants and concurrency scenarios
- [ ] `./mvnw test` and relevant IT tests pass

Start at **Step 0** of `data-consistency-implementation-sequence.md`. If scope is unclear, **stop and ask**.
```
