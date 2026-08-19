### 4. `tasks/architectural-purity-implementation-sequence.md`

```markdown
# Architectural Purity — Implementation Sequence (P1/P2)

**Companions:** `architectural-purity-spec.md` · `architectural-purity-backlog.md`
**Rule:** Finish each step’s “Done when” before the next. Do not invent scope.

---

## Step 0 — Analysis

1. Search for Spring Security types (`UserDetails`, `Authentication`, `AuthenticationManager`, etc.) inside `domain` and `application`
2. Search for repository or infrastructure imports inside `infrastructure/web` controllers
3. Identify every controller that currently injects a repository or `JwtService` directly
4. Check whether ArchUnit is already on the classpath; if not, note that approval is required

**Done when:** Complete inventory of remaining violations exists.

---

## Step 1 — Clean application ports

- Change any use-case interface that returns or accepts Spring Security types to use pure domain or application DTOs instead
- Update the corresponding application services and callers
- Keep behaviour identical

**Done when:** No Spring Security types remain in application ports; unit tests still pass.

---

## Step 2 — Thin controllers

- For every controller that injects a repository or infrastructure service:
  - Introduce or reuse a proper inbound port (`*UseCase`)
  - Move the logic into the application service
  - Leave the controller only with mapping + delegation
- Pay special attention to `PlaylistController`, `LikeController` and `AuthenticationController`

**Done when:** Controllers depend only on use-case interfaces.

---

## Step 3 — Authentication encapsulation

- Ensure JWT generation and `UserDetails` construction stay inside infrastructure or a dedicated authentication application service
- Application ports speak only in domain terms (user id, roles as value objects or simple strings, etc.)

**Done when:** Authentication flow no longer leaks infrastructure types upward.

---

## Step 4 — ArchUnit setup

- Add ArchUnit (only if already approved or already present)
- Create a test class, e.g. `CleanArchitectureTest` or `HexagonalArchitectureTest`
- Start with the most important rules

**Done when:** ArchUnit runs and the first rules are evaluated.

---

## Step 5 — Core architecture rules

Implement at least the following rules:

- Classes in `..domain..` must not depend on `org.springframework..`, `software.amazon..`, `com.spotpobre.backend.infrastructure..`, etc.
- Classes in `..application..` must not depend on `..infrastructure..` or web packages
- Controllers must not depend on `..repository..` or concrete adapters

Make the rules strict enough to catch the violations fixed in previous steps.

**Done when:** ArchUnit tests are green and would fail if the old violations were re-introduced.

---

## Step 6 — Final verification

- Run the full test suite (unit + IT + architecture tests)
- Confirm no functional regression
- Update `AGENTS.md` and coding standards to mention the ArchUnit protection
- Update CHANGELOG if required

**Done when:** Definition of Done is fully met.

---

## Smoke path

1. `./mvnw test` passes, including the new ArchUnit class
2. Temporarily add a forbidden import in domain or a controller → ArchUnit fails
3. Revert the forbidden import → tests become green again
4. Main business flows (auth, playlist mutations, song streaming) still work

---

_Pre-implementation sequence. After delivery, replace with an as-built status note._
```
