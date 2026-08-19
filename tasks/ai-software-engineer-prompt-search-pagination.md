### 1. `tasks/ai-software-engineer-prompt-search-pagination.md`

```markdown
# AI Software Engineer Prompt — Search & Pagination Correctness (P1)

**Status:** Not implemented — high-priority correctness epic.
**Target:** Fix search case-sensitivity and real cursor-based pagination on DynamoDB
**Package:** `com.spotpobre.backend`

You implement the complete fix so that search and pagination behave correctly and predictably.

---

## Sources of truth (read in order)

1. `AGENTS.md`
2. `docs/coding-standards.md` · `docs/testing-playbook.md` · `docs/lessons.md`
3. `tasks/search-pagination-spec.md` — what to build
4. `tasks/search-pagination-backlog.md` — stories
5. `tasks/search-pagination-implementation-sequence.md` — build order
6. Reference: `DynamoDbSongMetadataRepositoryImpl`, artist search repository, playlist cursor pagination, domain/application ports for search

---

## Goal

Search must find results regardless of case, and pagination must advance correctly using DynamoDB cursors (`ExclusiveStartKey`).
Currently:

- Search lowercases the query but the stored sort key keeps original case → `begins_with` fails.
- Song and artist “pagination” ignores `pageNumber`, never uses `ExclusiveStartKey`, and returns wrong metadata.
- Requesting page 2 returns the first page again.
- `totalElements` / `totalPages` are fake (size of the current batch).

---

## Non-negotiable rules

- Prefer real DynamoDB cursor-based pagination over fake offset pagination
- Domain and application stay free of AWS SDK / DynamoDB types (use domain pagination model)
- Normalize data at write time or query time consistently (decide and document)
- Enforce a maximum `pageSize`
- English only
- No new Maven dependencies without human approval
- Existing public API shape should stay compatible unless a deliberate, documented change is required
- Do not push unless the human asks

---

## Definition of Done (epic)

- [ ] Case-insensitive search works for songs and artists
- [ ] Pagination advances correctly (second page ≠ first page)
- [ ] Cursor / `ExclusiveStartKey` is properly used and round-tripped
- [ ] Metadata (`hasNext`, next cursor, etc.) is honest
- [ ] Maximum page size is enforced
- [ ] Unit + integration tests cover the scenarios
- [ ] `./mvnw test` and relevant IT tests pass

Start at **Step 0** of `search-pagination-implementation-sequence.md`. If scope is unclear, **stop and ask**.
```
