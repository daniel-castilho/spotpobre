### 4. `tasks/http-contracts-implementation-sequence.md`

```markdown
# HTTP Contracts & Error Handling — Implementation Sequence (P1)

**Companions:** `http-contracts-spec.md` · `http-contracts-backlog.md`
**Rule:** Finish each step’s “Done when” before the next. Do not invent scope.

---

## Step 0 — Analysis

1. Inspect current `GlobalExceptionHandler` and the exceptions it already handles
2. Inspect JWT filter and what happens on malformed / expired tokens
3. Inspect `SecurityConfig` for existing entry points / handlers
4. List the most common business errors that today become 400

**Done when:** Clear inventory of current mappings and gaps exists.

---

## Step 1 — Typed exceptions

- Create or standardise:
  - `NotFoundException`
  - `ConflictException`
  - `ForbiddenException`
  - `UnauthorizedException` (if needed)
  - Keep existing validation exceptions for 400
- Place them in the appropriate package (domain or application) following project conventions.
- Do **not** put HTTP status codes inside the domain exceptions.

**Done when:** Exception classes exist and compile.

---

## Step 2 — Standard error envelope

- Confirm the canonical error response structure used by the API.
- If multiple formats exist, choose one and make it the single standard.
- Ensure it can carry message, optional code, and optional details.

**Done when:** One clear envelope is defined and reusable.

---

## Step 3 — GlobalExceptionHandler

- Map each typed exception to the correct HTTP status:
  - NotFound → 404
  - Conflict → 409
  - Forbidden → 403
  - Unauthorized → 401
  - Validation / illegal argument → 400
- Build the standard envelope in every case.
- Keep any useful logging.

**Done when:** Handler unit tests prove the mappings.

---

## Step 4 — Security integration

- Implement `AuthenticationEntryPoint` that writes 401 + standard envelope.
- Implement `AccessDeniedHandler` that writes 403 + standard envelope.
- Register both in `SecurityConfig`.

**Done when:** Unauthenticated and forbidden requests return the same JSON shape as the rest of the API.

---

## Step 5 — JWT filter hardening

- Catch token parsing / validation errors inside the filter.
- Translate them into a clean 401 response (via entry point or by writing the envelope directly in a controlled way).
- Ensure no unhandled exception escapes as a raw 500.

**Done when:** Malformed and expired tokens produce 401 with the standard body.

---

## Step 6 — Application layer adoption

- Update services and use cases that currently throw generic exceptions to throw the new typed ones (especially NotFound, Conflict, Forbidden).
- Focus on the paths already identified in the security and data-consistency epics (playlist ownership, email uniqueness, missing resources).

**Done when:** Main business flows throw the correct exception types.

---

## Step 7 — Tests

- Unit tests for the exception handler
- E2E or IT tests for:
  - 401 (no token / bad token)
  - 403 (authenticated but not owner)
  - 404 (resource not found)
  - 409 (duplicate email or equivalent)
  - 400 (validation)
- Confirm happy paths still return 200/201.

**Done when:** Tests are green.

---

## Step 8 — Final verification

- Full relevant suite passes
- Manual smoke of the main error scenarios
- Update CHANGELOG / AGENTS.md if required by project rules

**Done when:** Definition of Done is fully met.

---

## Smoke path

1. Request a protected endpoint without token → 401 + standard body
2. Authenticated user B tries to modify user A’s playlist → 403 + standard body
3. Request a non-existent song/playlist → 404 + standard body
4. Register the same email twice → 409 + standard body
5. Send invalid payload → 400 + standard body
6. Happy-path requests still succeed

---

_Pre-implementation sequence. After delivery, replace with an as-built status note._
```
