### 1. `tasks/ai-software-engineer-prompt-architectural-purity.md`

```markdown
# AI Software Engineer Prompt — Architectural Purity (P1/P2)

**Status:** Not implemented — architecture enforcement epic.
**Target:** Eliminate remaining framework leakage and protect Clean Architecture boundaries
**Package:** `com.spotpobre.backend`

You implement the remaining Clean Architecture fixes so that domain and application stay free of frameworks, controllers depend only on inbound ports, and architectural rules are automatically enforced.

---

## Sources of truth (read in order)

1. `AGENTS.md`
2. `docs/coding-standards.md` · `docs/testing-playbook.md` · `docs/lessons.md`
3. `tasks/architectural-purity-spec.md` — what to build
4. `tasks/architectural-purity-backlog.md` — stories
5. `tasks/architectural-purity-implementation-sequence.md` — build order
6. Reference: current package structure, existing ports, controllers that still call repositories directly, any remaining Spring Security types in application

---

## Goal

Bring the codebase in line with the Clean Architecture rules declared in the project:

- Domain and application must not depend on Spring, Spring Security, AWS, MapStruct, etc.
- Controllers must depend only on application inbound ports (`*UseCase`), never on repositories or infrastructure services directly.
- No Spring Security types (`UserDetails`, `AuthenticationManager`, etc.) leak into application ports or domain.
- Architectural boundaries are protected by automated ArchUnit tests so regressions are impossible to merge unnoticed.

---

## Non-negotiable rules

- Domain remains 100% pure Java (already largely achieved — keep it that way)
- Application may use Spring stereotypes only if the project explicitly allows them; prefer pure use-case interfaces + plain services where possible
- Never return or accept Spring Security types from application ports
- Controllers are thin: map HTTP → command/query → call use case → map result
- Add ArchUnit rules that fail the build on violations
- English only
- No new Maven dependencies without human approval (ArchUnit is already commonly used or may be added only with explicit approval)
- Do not push unless the human asks

---

## Definition of Done (epic)

- [ ] No Spring Security types in domain or application ports
- [ ] Controllers depend only on inbound ports
- [ ] No direct repository calls from controllers
- [ ] ArchUnit tests enforce the package and dependency rules
- [ ] Existing behaviour remains unchanged
- [ ] `./mvnw test` passes (including the new architecture tests)

Start at **Step 0** of `architectural-purity-implementation-sequence.md`. If scope is unclear, **stop and ask**.
```
