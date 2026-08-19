### 1. `tasks/ai-software-engineer-prompt-http-contracts.md`

```markdown
# AI Software Engineer Prompt — HTTP Contracts & Error Handling (P1)

**Status:** Not implemented — high-priority API correctness epic.
**Target:** Consistent, meaningful HTTP status codes and uniform error responses
**Package:** `com.spotpobre.backend`

You implement the complete improvement of exception-to-HTTP mapping and security error handling so the API returns predictable, correct status codes and a uniform error envelope.

---

## Sources of truth (read in order)

1. `AGENTS.md`
2. `docs/coding-standards.md` · `docs/testing-playbook.md` · `docs/lessons.md`
3. `tasks/http-contracts-spec.md` — what to build
4. `tasks/http-contracts-backlog.md` — stories
5. `tasks/http-contracts-implementation-sequence.md` — build order
6. Reference: `GlobalExceptionHandler`, existing domain/application exceptions, JWT filter, `SecurityConfig`

---

## Goal

The API must return the correct HTTP status code for each class of error and always use the same JSON error envelope.

Currently almost every business error is mapped to 400. Security-related failures (JWT filter, authentication, authorization) may bypass the `@RestControllerAdvice` and produce inconsistent or unstructured responses.

---

## Non-negotiable rules

- Introduce or reuse typed exceptions for the main error categories (NotFound, Conflict, Forbidden, Unauthorized, etc.)
- Map those exceptions to the proper HTTP status codes
- Security entry points (`AuthenticationEntryPoint`, `AccessDeniedHandler`) must produce the **same** error envelope used by the rest of the API
- JWT filter must not leak raw 500s for malformed/expired tokens
- Domain and application stay free of servlet/HTTP types
- English only
- No new Maven dependencies without human approval
- Do not push unless the human asks

---

## Definition of Done (epic)

- [ ] Business exceptions map to correct status codes (404, 409, 403, 401, 400, …)
- [ ] Security failures (unauthenticated / unauthorized) return the standard JSON envelope with 401/403
- [ ] Malformed or expired JWT is handled cleanly
- [ ] Existing happy-path behaviour is unchanged
- [ ] Tests cover the main error mappings
- [ ] `./mvnw test` and relevant IT/E2E tests pass

Start at **Step 0** of `http-contracts-implementation-sequence.md`. If scope is unclear, **stop and ask**.
```
