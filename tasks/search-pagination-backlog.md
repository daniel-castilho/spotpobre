### 3. `tasks/search-pagination-backlog.md`

```markdown
# Search & Pagination Correctness — Backlog (P1)

**Companions:** `search-pagination-spec.md` · `search-pagination-implementation-sequence.md`
**Epic goal:** Correct, predictable search and pagination on DynamoDB.

**MVP:** S1–S9

---

## Story map
```

FOUNDATION
S1 Domain pagination model (pure)
S2 Normalization strategy decision

SEARCH
S3 Case-insensitive song search
S4 Case-insensitive artist search

PAGINATION
S5 Real cursor pagination for songs
S6 Real cursor pagination for artists
S7 Align playlist listing metadata

SAFETY & QUALITY
S8 Max pageSize + validation
S9 Integration tests (LocalStack)

```

---

## Stories

| ID | Story | Priority | Notes |
|----|--------|----------|--------|
| S1 | Ensure pure domain pagination types are used (no Spring Data / DynamoDbPage in domain or application) | Must | Architecture rule |
| S2 | Decide and implement normalization (write-time lowercase sort key recommended) | Must | |
| S3 | Song search becomes case-insensitive | Must | |
| S4 | Artist search becomes case-insensitive | Must | |
| S5 | Song search/list uses ExclusiveStartKey + returns next cursor | Must | |
| S6 | Artist search/list uses ExclusiveStartKey + returns next cursor | Must | |
| S7 | Playlist listing metadata is honest (hasNext / next cursor) | Should | Already partially correct |
| S8 | Enforce maximum pageSize | Must | |
| S9 | ITs proving case-insensitive search + multi-page behaviour | Must | |

---

## Definition of Done (epic)

- [x] S1–S9 done
- [x] Search “test” finds “Test Song”
- [x] Page 2 returns different items from page 1
- [x] Relevant tests green

---

## Status

**Done.** Shipped on `main` as part of the Unreleased work (see `CHANGELOG.md`). Case-insensitive
search via write-time-normalized `searchTitle`/`searchName` sort keys; real cursor pagination
(`ExclusiveStartKey`) on song/artist search; `limit` capped at `PageRequest.MAX_PAGE_SIZE` (50);
invalid cursors → 400. Unit tests (134) and the full `*IT` suite (19) pass.
```
