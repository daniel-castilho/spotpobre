### 2. `tasks/search-pagination-spec.md`

```markdown
# Search & Pagination Correctness — Technical Specification (P1)

**Status:** Draft for implementation (high priority)
**Focus:** Case-insensitive search + real cursor-based pagination on DynamoDB
**Companions:** `search-pagination-backlog.md` · `search-pagination-implementation-sequence.md`

---

## 1. Purpose & scope

Make search and list endpoints behave correctly.

**In scope (P1):**

- Case-insensitive search for songs (by title) and artists (by name)
- Real pagination using DynamoDB `ExclusiveStartKey` / cursor
- Honest pagination metadata (at least `hasNext` + next cursor)
- Maximum allowed `pageSize`
- Consistent domain-level pagination model (no Spring Data `Page`/`Pageable` leakage, no `DynamoDbPage` leakage into domain/application)
- Tests that prove page 2 is different from page 1 and that search finds mixed-case data

**Out of scope:**

- Full-text search engine (OpenSearch, etc.)
- Changing the public response shape dramatically (keep compatibility where possible)
- Pagination of every possible list endpoint (focus on the broken search + main list endpoints)
- Re-introducing exact total counts if they require expensive scans (prefer cursor model)

---

## 2. Current problems (summary)

| Area         | Problem                                             | Consequence                       |
| ------------ | --------------------------------------------------- | --------------------------------- |
| Search       | Query lowercased, sort key not normalized           | “test” does not find “Test Song”  |
| Pagination   | `pageNumber` ignored, no `ExclusiveStartKey`        | Page 2 returns page 1 again       |
| Metadata     | `totalElements` = batch size, `totalPages` = 1      | Clients receive lies              |
| Safety       | No max `pageSize`                                   | Potential abuse / large reads     |
| Architecture | Possible leakage of infrastructure pagination types | Violates Clean Architecture rules |

---

## 3. Target behaviour

**Search**

- Searching “beatles”, “Beatles” or “BEATLES” returns the same results.
- Normalization strategy must be consistent at write and/or read time (document the choice).

**Pagination**

- Use cursor-based pagination (DynamoDB native style).
- Client sends `limit` (pageSize) + optional `cursor` (opaque).
- Response contains the items + `nextCursor` (or equivalent) + `hasNext`.
- Do **not** pretend to support random page numbers if the underlying store is cursor-based.

**Domain**

- Application and domain speak only in domain pagination types (`PageRequest` / `PageResult` or the project’s existing pure model).

---

## 4. Expected touch-points

- Song search repository / adapter
- Artist search repository / adapter
- Playlist listing (already closer to correct — align metadata)
- Domain pagination types (if still leaking infrastructure types)
- Controllers / DTOs that expose page/cursor parameters
- Integration tests with LocalStack

---

## 5. Testing requirements

- Search finds results regardless of case
- First page and second page return different items when enough data exists
- Invalid or missing cursor is handled safely
- `pageSize` above the maximum is rejected or capped
- Round-trip of the cursor works

---

## 6. Definition of Done

- [x] Case-insensitive search works
- [x] Real cursor pagination works
- [x] Metadata is truthful
- [x] Max page size enforced
- [x] Tests prove the above
- [x] No new infrastructure leakage into domain/application

---

## 7. Status

**Done.** Shipped on `main` as part of the Unreleased work (see `CHANGELOG.md`); unit tests (134)
and the full `*IT` suite (19) pass.
```
