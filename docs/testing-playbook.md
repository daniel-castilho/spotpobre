# Testing Playbook

**Role:** Write and interpret tests for this Java 21 / Spring Boot 3.5 Clean Architecture service
(DynamoDB + S3 via LocalStack, Redis, JWT).
**Stack constraints:** JUnit 5 + Mockito + Testcontainers (LocalStack) + RestAssured only — no new
test dependency without human approval (`AGENTS.md` rule 5).

Sources: `AGENTS.md` · `docs/coding-standards.md` · colocated `*Test` / `*IT` classes · `README.md`

---

## Pyramid

1. **Domain unit** — pure invariants and rich rules (`ArtistTest`, `PlaylistTest`, `SongTest`,
   `UserTest`). **No mocks**, no Spring.
2. **Application unit** — use-case services with **mocked domain ports only**; happy path +
   rejection + ownership/authorization behaviour.
3. **Slice integration (selective)** — real DynamoDB adapter behind Testcontainers + LocalStack
   (`DynamoDbPlaylistRepositoryAdapterTest`). Requires Docker.
4. **End-to-end** — RestAssured against the full app on `RANDOM_PORT` (`AuthenticationFlowIT`,
   `ArtistSongFlowIT`, `PlaylistFlowIT`). Requires Docker; run explicitly.
5. **Smoke** — `docker-compose up -d` + LocalStack setup + `./mvnw spring-boot:run`; exercise the
   auth → playlist flow with HTTP calls.

This is a **JSON API** project: exercise **use cases / ports** in unit tests and **HTTP flows** in
the `*IT` suite. Controllers are thin — keep business assertions in application tests. Never
assert against `infrastructure` internals from `domain`/`application` tests.

---

## Runner & layout

```bash
./mvnw test                 # fast unit + slice loop (Docker only for the adapter test)
./mvnw test -Dtest='*IT'    # E2E explicitly (Docker + LocalStack)
./mvnw clean package        # full build gate
```

- Tests live in `src/test/java/...` mirrored to production packages:
  - `.../domain/<feature>/model/*Test.java`
  - `.../application/<feature>/service/*ServiceTest.java`
  - `.../infrastructure/persistence/kv/adapter/*AdapterTest.java`
  - `.../*IT.java` for end-to-end flows.
- English names: `register_duplicateEmail_throws`, or descriptive `should reject a duplicate email`.
- Assert with JUnit 5 `org.junit.jupiter.api.Assertions`. AssertJ's `assertThat` appears only in the
  context-load smoke (`SpotpobreApplicationTests`) — prefer `Assertions` in unit tests. Keep to the
  style already present in the file you extend.

---

## Mandatory patterns

| Pattern                 | Rule                                                                                                  |
| ----------------------- | ----------------------------------------------------------------------------------------------------- |
| Domain tests            | No mocks; pure entities / value objects only                                                          |
| Application tests       | Mock **domain ports** only (`UserRepository`, `PlaylistRepository`, `SongMetadataRepository`, ...)    |
| Business-rule failures  | Services throw `IllegalStateException` or `IllegalArgumentException`; assert the message / condition — do not rely on Spring context |
| Ownership               | Playlist mutation is owner-scoped: assert non-owner operations are rejected                            |
| Passwords               | Always through the domain `PasswordHasher` port (adapter uses Argon2id); never assert or log plaintext |
| Pagination              | Owner list is paginated; assert ordering and paging behaviour through the domain port mock             |
| Storage                 | Song flows mock `SongStoragePort`; S3 behaviour is only exercised in the slice/E2E tests               |
| Boundary grep           | `domain/` and `application/` remain free of `infrastructure.*`, AWS SDK, MapStruct, springdoc, `org.springframework.web` (rule-1 grep must not add new matches) |

---

## Current automated suite (map)

| Area             | File(s)                                                            | Focus                                                     |
| ---------------- | ------------------------------------------------------------------ | --------------------------------------------------------- |
| Auth register    | `application/user/service/RegisterUserServiceTest`                 | Hashed password; duplicate email rejected; roles          |
| Auth login       | `application/user/service/AuthenticationServiceTest`               | Success; bad credentials; token issued                    |
| User profile     | `GetUserProfileServiceTest`, `GetUserDetailsServiceTest`    | Returns own profile; not-found handling                   |
| Artist           | `CreateArtistServiceTest`, `SearchArtistsServiceTest`              | Create (admin rule at HTTP layer); search by name         |
| Playlists        | `Create/Delete/Update/GetPlaylist*ServiceTest`                     | CRUD; owner authorization; paginated owner listing        |
| Playlist songs   | `AddSongToPlaylistServiceTest`, `RemoveSongFromPlaylistServiceTest`| Membership guards; missing song/playlist rejected         |
| Songs            | `Upload/GetSongMetadata/GetSongStreamUrl/SearchSongsServiceTest`   | Upload to storage port; metadata; stream URL; search      |
| Albums           | `application/album/service/CreateAlbumServiceTest`                 | Artist existence; album persisted                          |
| Likes            | `ToggleLikeServiceTest`, `LikeStrategyFactoryTest`, `SongLikeStrategyTest`, `ArtistLikeStrategyTest`, `PlaylistLikeStrategyTest` | Toggle add/remove; strategy dispatch; entity-existence guards |
| Error mapping    | `infrastructure/web/exception/GlobalExceptionHandlerTest`          | 400/401/403/500 mapping, incl. `BadCredentialsException` → 401 |
| Domain models    | `domain/<feature>/model/*Test`                                     | Entity invariants (no mocks)                              |
| Slice (DynamoDB) | `infrastructure/.../DynamoDbPlaylistRepositoryAdapterTest`         | Real adapter against LocalStack                           |
| E2E flows        | `AuthenticationFlowIT`, `ArtistSongFlowIT`, `PlaylistFlowIT`       | Full HTTP flows on `RANDOM_PORT`                          |

When you change behaviour covered above, **extend the existing file** instead of inventing a
parallel suite.

---

## Known coverage gaps

The previous gaps (Album flow, Like flow, bad-password HTTP status) are **closed** by the unit
tests in the map above. Remaining hard-to-unit-test behaviour lives in the `*IT` / slice layers.

**Known API gap (resolved):** wrong credentials at `/api/v1/auth/authenticate` now map
`BadCredentialsException` → **401** (`GlobalExceptionHandler.handleBadCredentials`), with a generic
message that never leaks whether the user exists. Pinned by `GlobalExceptionHandlerTest` (unit) and
`AuthenticationFlowIT` (E2E).

---

## Regression checklist

| Area                       | Must verify                                                                                              |
| -------------------------- | -------------------------------------------------------------------------------------------------------- |
| **Auth — register**        | Password stored hashed (via `PasswordHasher` → Argon2id); duplicate email rejected; `ROLE_USER` assigned |
| **Auth — authenticate**    | Valid credentials return JWT; wrong password → 401; unknown user rejected without leaking existence         |
| **Artists**                | `POST /api/v1/artists` requires `ROLE_ADMIN`; search by name returns only matches                          |
| **Albums**                 | `POST /api/v1/albums` validates artist existence; `POST /albums/{id}/songs` initiates presigned upload via `SongStoragePort` |
| **Songs**                  | Initiate persists metadata without file bytes; confirm verifies storage; stream URL resolves; search by title matches; missing id → 404 |
| **Playlists**              | CRUD owner-scoped; rename/delete/add/remove guards; owner list paginated; non-owner rejected              |
| **Likes**                  | Toggle on song/artist/playlist; reverse query returns likes for an entity                                  |
| **Security**               | Every endpoint has an explicit `SecurityConfig` rule; mutating routes never permit-all                     |
| **Boundaries**             | Rule-1 grep returns no **new** matches; pagination uses domain `PageRequest`/`PageResult` only |
| **Stack**                  | No new test dependencies; `./mvnw test` green; `*IT` green after persistence/storage/security changes     |

**Playlist authorization regression (application):** create playlist as owner A → owner B
rename/delete/add/remove → rejected; owner A still succeeds.

**Song upload regression (application):** `InitiateSongUploadServiceTest` — missing album
rejected; unsupported content type rejected before storage; storage port invoked exactly once on
success; metadata persisted after URL generation. `ConfirmSongUploadServiceTest` — album/storage
key mismatch rejected without calling storage.

---

## Release regression smoke (API)

Run against the **local stack** before a release tag:

```bash
docker-compose up -d        # LocalStack (DynamoDB + S3) + Redis
# run the LocalStack setup commands from README.md (buckets + tables)
./mvnw spring-boot:run      # or ./mvnw test -Dtest='*IT' to automate the flows
```

**Required — stop and fix on first failure.**

| #  | Step                                            | Expected                                              |
| -- | ----------------------------------------------- | ----------------------------------------------------- |
| 1  | `POST /api/v1/auth/register`                    | 200 with JWT; `ROLE_USER`; no plaintext password anywhere |
| 2  | `POST /api/v1/auth/authenticate`                | 200 with JWT; wrong password → **401**               |
| 3  | `POST /api/v1/artists` as non-admin             | 403                                                    |
| 4  | `POST /api/v1/artists` as admin                 | 201; searchable by name                                 |
| 5  | `POST /api/v1/albums` + initiate + PUT to presigned URL + confirm | 201 then 200; metadata + S3 object; no file body through the API |
| 6  | `GET /api/v1/songs/{id}`                        | 200 with metadata + stream URL                          |
| 7  | Playlist CRUD as owner                          | Create → list → rename → add/remove song → delete      |
| 8  | Playlist mutation as non-owner                  | Rejected (403/404)                                      |
| 9  | `POST /api/v1/likes/toggle` (song/artist/playlist) | Toggles; reverse lookup consistent                      |
| 10 | `GET /api/v1/users/me` without token            | 401; with token → profile                               |
| 11 | `/actuator/health`                              | 200 `UP`                                                |

**Optional (do not block tag):** Swagger UI renders endpoints; `/actuator/info` + `/actuator/metrics`.

---

## Quality gates (CI local mirror)

```bash
./mvnw test                  # fast unit + slice
./mvnw test -Dtest='*IT'     # E2E (Docker)
./mvnw clean package         # production build
grep -rEn "^import (com\.spotpobre\.backend\.infrastructure|software\.amazon|io\.awspring|org\.mapstruct|org\.springdoc|org\.springframework\.web)" src/main/java/com/spotpobre/backend/domain src/main/java/com/spotpobre/backend/application
```

There is no CI pipeline yet — run the four commands locally and keep them green. The boundary grep
must return nothing (no tracked leaks in `domain/`/`application/`; see `AGENTS.md`).

---

## Reading failures

| Class            | Signal                                        | First move                                                     |
| ---------------- | --------------------------------------------- | -------------------------------------------------------------- |
| **Logic**        | Assertion failure in a service/domain test    | Fix the use case or the wrong expectation                       |
| **Boundary**     | Forbidden import in domain/application        | Restore the port boundary — do not weaken the rule              |
| **Compile**      | Missing method on mock / changed signature    | Update the mock setup; verify the port surface                 |
| **DynamoDB/LocalStack** | Table/bucket missing, connection refused | Run the README setup commands; `docker-compose up -d`          |
| **Flaky / env**  | Docker, port, Redis connection                | Re-run; fix compose — don't skip tests                          |
| **E2E**          | `*IT` not picked up                           | Run explicitly with `./mvnw test -Dtest='*IT'` (no failsafe yet)|

**Priority when many fail:** boundary grep → domain invariants → touched application services →
slice adapter test → E2E flows.

---

## Analyzer reply format

```text
## Summary
Class / test (Logic|Boundary|Compile|DynamoDB|Flaky|E2E)
Cause (one line)

## Fix plan
1. …
2. …

## Verify
./mvnw test
# optionally: ./mvnw test -Dtest='*IT'   (Docker)
# smoke: docker-compose up -d && ./mvnw spring-boot:run
```

---

## Do not

- Skip, delete, or `@Disabled` tests to green the build
- Add a new test dependency without human approval
- Mock `infrastructure` classes from `domain`/`application` tests
- Assert or log plaintext passwords or JWT secrets
- Weaken an existing assertion to make a change pass
- Put business rules in controller/IT tests "because the flow failed"
- Widen `Page`/`Pageable`/`DynamoDbPage` use across the core — pagination goes through `PageRequest`/`PageResult`

---

## Done when

- [ ] Happy path + at least one rejection automated for the change
- [ ] New domain/application behaviour has a colocated `*Test`
- [ ] Ownership/authorization rules covered where the endpoint mutates a resource
- [ ] Failure analysis names root cause and smallest fix
- [ ] `./mvnw test` green (plus `-Dtest='*IT'` when persistence/storage/security touched)
- [ ] Smoke steps clear when HTTP behaviour changed
- [ ] Docs synced if milestone-sized (README Current State / CHANGELOG / AGENTS debt) per `AGENTS.md`