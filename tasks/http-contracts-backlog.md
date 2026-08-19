### 3. `tasks/http-contracts-backlog.md`

```markdown
# HTTP Contracts & Error Handling — Backlog (P1)

**Companions:** `http-contracts-spec.md` · `http-contracts-implementation-sequence.md`
**Epic goal:** Predictable HTTP status codes and uniform error responses.

**MVP:** S1–S8

---

## Story map
```

FOUNDATION
S1 Typed exception hierarchy
S2 Standard error envelope

MAPPING
S3 GlobalExceptionHandler mappings
S4 Security entry points (401 / 403)

JWT & FILTER
S5 Clean JWT error handling

COVERAGE
S6 Replace generic throws in services
S7 Tests for status codes
S8 Final verification

```

---

## Stories

| ID | Story | Priority | Notes |
|----|--------|----------|--------|
| S1 | Introduce/reuse typed exceptions (NotFound, Conflict, Forbidden, Unauthorized, …) | Must | |
| S2 | Confirm or create a single standard error response envelope | Must | |
| S3 | Map exceptions to correct HTTP status codes in GlobalExceptionHandler | Must | |
| S4 | Implement AuthenticationEntryPoint + AccessDeniedHandler with same envelope | Must | |
| S5 | JWT filter handles malformed/expired tokens without raw 500 | Must | |
| S6 | Update application services to throw the new typed exceptions | Must | |
| S7 | Unit + E2E/IT tests for the main status codes | Must | |
| S8 | Regression check on happy paths + documentation | Must | |

---

## Definition of Done (epic)

- [ ] S1–S8 done
- [ ] 404 / 409 / 403 / 401 returned in the appropriate scenarios
- [ ] Error body is consistent across MVC and Security
- [ ] Relevant tests green

---

## Status

**Not started.** High-priority P1.
```
