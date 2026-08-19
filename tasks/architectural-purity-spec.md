### 2. `tasks/architectural-purity-spec.md`

```markdown
# Architectural Purity — Technical Specification (P1/P2)

**Status:** Draft for implementation
**Focus:** Remove remaining framework leakage and enforce Clean Architecture with ArchUnit
**Companions:** `architectural-purity-backlog.md` · `architectural-purity-implementation-sequence.md`

---

## 1. Purpose & scope

Close the gap between the declared architecture and the actual code.

**In scope (P1/P2):**

- Remove any remaining Spring Security types (`UserDetails`, etc.) from application ports and services
- Stop controllers from calling repositories or infrastructure services directly
- Ensure all HTTP entry points go through inbound ports (`*UseCase`)
- Encapsulate JWT generation / authentication details behind proper ports or application services
- Introduce (or complete) ArchUnit tests that protect:
  - Domain must not depend on any framework or infrastructure
  - Application must not depend on infrastructure or web
  - Controllers must not depend on repositories or adapters directly
- Keep public API behaviour identical

**Out of scope:**

- Large redesign of the package structure
- Removing every Spring annotation from application if the project standard still accepts `@Service` / `@Transactional` there
- Rewriting working business logic
- Performance optimisations

---

## 2. Current violations (from analysis)

| Violation                                                          | Location                               | Impact                                |
| ------------------------------------------------------------------ | -------------------------------------- | ------------------------------------- |
| Application port returns `UserDetails`                             | `GetUserDetailsUseCase`                | Framework leak                        |
| Controllers call `UserRepository` directly                         | `PlaylistController`, `LikeController` | Bypasses application layer            |
| AuthenticationController knows `JwtService` and builds authorities | Authentication flow                    | Infrastructure details in wrong place |
| Missing automated architecture tests                               | Whole project                          | Regressions possible                  |

(Other leaks such as `Page`/`Pageable` and `DynamoDbPage` should already have been addressed in earlier epics; verify and close any remaining ones.)

---

## 3. Target rules

1. **Domain**
   - Zero dependencies on Spring, AWS, MapStruct, Jackson, etc.

2. **Application**
   - Depends only on domain
   - Inbound ports (`*UseCase`) and outbound ports are pure interfaces
   - No Spring Security types cross the port boundary

3. **Infrastructure / Web**
   - Controllers depend exclusively on application inbound ports
   - Adapters implement domain/application outbound ports
   - JWT, Security, DynamoDB, S3 stay inside infrastructure

4. **Enforcement**
   - ArchUnit tests fail the build when the rules above are broken

---

## 4. Expected changes

- Refactor `GetUserDetailsUseCase` (and similar) to return a domain or application DTO instead of `UserDetails`
- Move any direct repository calls from controllers into proper use cases
- Hide `JwtService` behind an application port or keep it strictly inside the authentication use case / infrastructure
- Add a dedicated ArchUnit test class (e.g. `HexagonalArchitectureTest` or `CleanArchitectureTest`)

---

## 5. Testing requirements

- Existing functional tests remain green (no behaviour change)
- New ArchUnit tests cover the critical package dependency rules
- At least one negative test (or commented example) showing that a forbidden dependency would fail the build

---

## 6. Definition of Done

- [ ] No framework types in domain or application ports
- [ ] Controllers are thin and only call use cases
- [ ] ArchUnit rules are active and green
- [ ] Full test suite passes
- [ ] AGENTS.md / coding standards updated if the rules become stricter
```
