### 3. `tasks/architectural-purity-backlog.md`

```markdown
# Architectural Purity — Backlog (P1/P2)

**Companions:** `architectural-purity-spec.md` · `architectural-purity-implementation-sequence.md`
**Epic goal:** Make the declared Clean Architecture real and self-protecting.

**MVP:** S1–S7

---

## Story map
```

CLEANUP
S1 Remove Spring Security types from application ports
S2 Stop controllers from calling repositories directly
S3 Encapsulate JWT / authentication details

ENFORCEMENT
S4 Introduce ArchUnit dependency rules
S5 Protect domain purity
S6 Protect application and web boundaries

VERIFICATION
S7 Full regression + documentation

```

---

## Stories

| ID | Story | Priority | Notes |
|----|--------|----------|--------|
| S1 | Replace `UserDetails` (and similar) with pure types in application ports | Must | |
| S2 | Refactor controllers so they only depend on `*UseCase` ports | Must | Playlist, Like, etc. |
| S3 | Hide `JwtService` and authority building behind proper application/infrastructure boundaries | Must | |
| S4 | Add ArchUnit test class with the core dependency rules | Must | |
| S5 | ArchUnit rule: domain has zero framework/infrastructure dependencies | Must | |
| S6 | ArchUnit rules: application does not depend on infrastructure/web; controllers do not depend on repositories | Must | |
| S7 | Regression suite green + update AGENTS.md / docs if needed | Must | |

---

## Definition of Done (epic)

- [ ] S1–S7 done
- [ ] Architecture tests fail if someone re-introduces a forbidden dependency
- [ ] No behaviour change visible to API clients
- [ ] `./mvnw test` green

---

## Status

**Not started.** Important for long-term maintainability.
```
