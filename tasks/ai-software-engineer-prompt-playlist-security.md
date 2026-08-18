### 1. `tasks/ai-software-engineer-prompt-playlist-security.md`

```markdown
# AI Software Engineer Prompt — Playlist Security (P0)

**Status:** Not implemented — critical security epic.
**Target:** Playlist mutation authorization (IDOR fix)
**Package:** `com.spotpobre.backend`

You implement the complete ownership authorization for all playlist mutations in this Java 21 + Spring Boot 3 Clean Architecture backend.

---

## Sources of truth (read in order)

1. `AGENTS.md`
2. `docs/coding-standards.md` · `docs/testing-playbook.md` · `docs/lessons.md`
3. `tasks/playlist-security-spec.md` — what to build
4. `tasks/playlist-security-backlog.md` — stories
5. `tasks/playlist-security-implementation-sequence.md` — build order
6. Reference: existing playlist services, `SecurityConfig`, domain exceptions pattern

---

## Goal

Authenticated users can **only** mutate (update, delete, add song, remove song) playlists they own.
Any attempt by user B to modify a playlist belonging to user A must be rejected with a proper 403.

No changes to public read endpoints. No new features beyond ownership enforcement.

---

## Non-negotiable rules

- Clean Architecture must be preserved (domain and application free of Spring Security types where possible)
- Ownership check lives in the **application layer** (not only in controllers or SecurityConfig)
- `currentUserId` must come from the security context / authenticated principal — never from the request body alone
- Use typed domain/application exceptions (`ForbiddenException` or equivalent)
- English only
- No new Maven dependencies without human approval
- Unit tests + E2E tests (A-versus-B scenarios) required
- Do not push unless the human asks

---

## Definition of Done (epic)

- [ ] All playlist mutation use cases receive and enforce `currentUserId`
- [ ] Owner check performed in application services
- [ ] Proper 403 response for unauthorized access
- [ ] E2E tests covering User A vs User B for every mutation endpoint
- [ ] Existing happy-path tests still green
- [ ] `./mvnw test` and relevant `*IT` tests pass

Start at **Step 0** of `playlist-security-implementation-sequence.md`. If scope is unclear, **stop and ask**.
```
