### 4. `tasks/search-pagination-implementation-sequence.md`

```markdown
# Search & Pagination Correctness — Implementation Sequence (P1)

**Companions:** `search-pagination-spec.md` · `search-pagination-backlog.md`
**Rule:** Finish each step’s “Done when” before the next. Do not invent scope.

---

## Step 0 — Analysis

1. Inspect current song and artist search implementations
2. Confirm how the sort key is written and how the query is prepared
3. Inspect current pagination parameters and response mapping
4. Check whether domain/application still leak `Page`/`Pageable` or `DynamoDbPage`

**Done when:** Clear list of broken behaviours and files exists.

---

## Step 1 — Domain pagination model

- Guarantee that domain and application use only pure pagination types.
- If infrastructure types are still present, replace them now (align with earlier pagination debt fixes if already started).

**Done when:** No infrastructure pagination types remain in domain or application.

---

## Step 2 — Normalization strategy

- Decide: normalize on write (store lowercase sort key) **or** normalize on read (more complex with `begins_with`).
- Recommended: store a normalized (lowercase) search attribute / sort key at write time.
- Implement the chosen strategy for songs and artists.

**Done when:** New writes use the normalized form; decision is documented.

---

## Step 3 — Case-insensitive search

- Update song search query to use the normalized attribute.
- Update artist search query the same way.
- Keep the public search API behaviour stable.

**Done when:** Unit/IT can prove that mixed-case queries return the expected items.

---

## Step 4 — Real cursor pagination

- Replace fake offset pagination with DynamoDB cursor style:
  - Accept `limit` + optional `cursor`
  - Use `ExclusiveStartKey`
  - Return `nextCursor` / `hasNext`
- Apply to song and artist search/list endpoints.
- Align playlist listing metadata if it still reports fake totals.

**Done when:** Requesting the next page returns different items.

---

## Step 5 — Safety limits

- Enforce a maximum `pageSize` (reject or cap).
- Validate cursor format safely (bad cursor → clear 400, not 500).

**Done when:** Oversized requests are handled cleanly.

---

## Step 6 — Integration tests

Write LocalStack tests that:

1. Insert mixed-case songs/artists
2. Search with different casings → same results
3. Request page 1 and page 2 → different content
4. Walk the cursor until the end

**Done when:** ITs are green and stable.

---

## Step 7 — Final verification & docs

- Full relevant test suite green
- Update CHANGELOG / AGENTS.md if required
- Note any intentional API change (e.g. moving from page-number to cursor)

**Done when:** Definition of Done is fully met.

---

## Smoke path

1. Create songs “Test Song”, “another test”, “TEST”
2. Search `test` → all three appear (or according to the exact match rules)
3. Request first page (limit 2) → receive 2 items + next cursor
4. Request next page with the cursor → receive remaining items, different from page 1
5. Request with pageSize above maximum → rejected or capped

---

_Pre-implementation sequence. After delivery, replace with an as-built status note._

---

## As-built status (post-delivery)

All steps 0–7 shipped on `main`:

- **Step 1 — domain model:** `domain`/`application` already used only pure `PageRequest`/
  `PageResult` (no Spring Data leak); added `PageRequest.MAX_PAGE_SIZE = 50`.
- **Step 2 — normalization:** write-time lowercased sort keys `searchTitle`/`searchName` set by the
  persistence mappers; `title-search-index`/`name-search-index` sort keys moved to them
  (`DynamoDbConfig`, `AbstractIntegrationTest`, README `awslocal` block).
- **Step 3/4 — case-insensitive search:** queries lowercase the input before `sortBeginsWith`;
  proven by `SongSearchPaginationIT`/`ArtistSearchPaginationIT`.
- **Step 4 — cursor pagination:** `SongMetadataRepository.searchByTitle` /
  `ArtistRepository.searchByName` take an `exclusiveStartKey`; repo impls read the first DynamoDB
  page, encode `LastEvaluatedKey` via `DynamoDbCursorHelper`; search endpoints use `limit` +
  `cursor` and return `PageResponse` (`content` + `nextPageToken` + `hasNext`). Playlist listing
  already followed this pattern (now also reports `hasNext`).
- **Step 5 — safety:** search use cases reject `pageSize > MAX_PAGE_SIZE` (400); malformed cursor →
  400 (`IllegalArgumentException` mapped by `GlobalExceptionHandler`).
- **Step 6 — ITs:** `SongSearchPaginationIT`, `ArtistSearchPaginationIT` (mixed-case matches,
  page-walk, invalid cursor).
- **Step 7 — docs:** `CHANGELOG.md` (Unreleased), `README.md`, `AGENTS.md`, `docs/coding-standards.md`,
  `docs/lessons.md`, and these task docs updated. `DynamoDbCursorHelper` fixed to serialize scalar
  key maps (Jackson cannot round-trip SDK `AttributeValue`).
- Full suites: `./mvnw test` (134 unit) and `./mvnw test -Dtest='*IT'` (19) pass.
- **Smoke path 1–5** (mixed-case search, page 1/2 different, oversized pageSize rejected) are
  covered by the unit + IT suites above.
```
