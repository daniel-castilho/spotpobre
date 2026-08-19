### 2. `tasks/http-contracts-spec.md`

```markdown
# HTTP Contracts & Error Handling — Technical Specification (P1)

**Status:** Draft for implementation (high priority)
**Focus:** Correct status codes + uniform error envelope for business and security errors
**Companions:** `http-contracts-backlog.md` · `http-contracts-implementation-sequence.md`

---

## 1. Purpose & scope

Make the API contract predictable and semantically correct.

**In scope (P1):**

- Typed exceptions for the main error categories
- Correct mapping in `GlobalExceptionHandler` (or equivalent):
  - Resource not found → **404**
  - Conflict (e.g. email already registered, duplicate) → **409**
  - Forbidden (ownership / authorization) → **403**
  - Unauthenticated / invalid credentials → **401**
  - Validation / bad request → **400**
  - Payload too large → **413** (when applicable)
  - Unsupported media type → **415** (when applicable)
- `AuthenticationEntryPoint` and `AccessDeniedHandler` that emit the same JSON error envelope
- Clean handling of malformed, expired or invalid JWT inside the filter
- Consistent error response body across the whole API

**Out of scope:**

- Changing success response shapes
- Internationalization of error messages
- Full Problem Details (RFC 7807) adoption unless already partially present and easy to align
- Rate-limiting responses (later epic)

---

## 2. Current problems (summary)

| Situation                                | Current behaviour                         | Desired behaviour        |
| ---------------------------------------- | ----------------------------------------- | ------------------------ |
| Playlist / song / artist not found       | Often 400                                 | 404                      |
| Email already registered                 | 400                                       | 409                      |
| User B tries to mutate User A’s playlist | 400 or generic error                      | 403                      |
| Bad credentials / missing token          | Inconsistent                              | 401 + standard envelope  |
| Malformed JWT                            | Possible raw 500 or non-standard response | 401 + standard envelope  |
| Validation errors                        | 400 (ok)                                  | Keep 400 with clear body |

---

## 3. Target design

1. **Typed exceptions** in domain or application (e.g. `NotFoundException`, `ConflictException`, `ForbiddenException`, `UnauthorizedException`).
2. **Single GlobalExceptionHandler** (or clear hierarchy) that maps each type to the correct status and builds the standard error envelope.
3. **Security integration**:
   - `AuthenticationEntryPoint` → 401 + same envelope
   - `AccessDeniedHandler` → 403 + same envelope
4. JWT filter catches parsing/validation errors and either throws a typed exception or delegates to the entry point so the response stays consistent.

Domain and application must not depend on `HttpServletResponse` or Spring MVC types.

---

## 4. Expected touch-points

- Exception classes (new or existing)
- `GlobalExceptionHandler`
- JWT authentication filter
- `SecurityConfig` (register entry point and access denied handler)
- Controllers / services that currently throw generic exceptions
- Error response DTO / envelope
- Tests (unit for handler, E2E/IT for status codes)

---

## 5. Testing requirements

- Each major exception type produces the expected status code and envelope
- Unauthenticated request → 401 with standard body
- Authenticated but forbidden request → 403 with standard body
- Not-found and conflict scenarios return 404 / 409
- Happy paths remain unchanged

---

## 6. Definition of Done

- [ ] Correct status codes for the main error categories
- [ ] Uniform error envelope everywhere (including security)
- [ ] JWT problems handled cleanly
- [ ] Tests prove the mappings
- [ ] Documentation / CHANGELOG updated if required
```
