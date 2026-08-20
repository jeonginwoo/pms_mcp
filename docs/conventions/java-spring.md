# Java / Spring Boot Conventions

> Scope: both Boot apps — the PMS (`pms/`, with the embedded MCP server) and the
> AI host (`host/`). Referenced from `host/CLAUDE.md` and `pms/CLAUDE.md`, so it
> contains only rules that are "always true".

## 1. Comments

- **Classes & methods**: Javadoc `/** ... */`. Explain purpose and intent.
    - Use `@param`/`@return` only when they remove ambiguity.
- **Fields**: single-line `//` comment **only when it carries information the name does not** — a constraint, unit, default, or the decision it comes from. Restating the field name (`// 식별자` on `id`) is noise; omit it.
- **No HTML tags**: `<ul>`, `<p>`, `<code>` etc. are forbidden in Javadoc. Use `-` for lists and blank lines for paragraphs.
- **All Javadoc and comments are written in Korean.**
- **Exception**: no comments on default constructors, getters, or setters.

## 2. Formatting

- **Braces**: **always** required.
    - Forbidden: `if (cond) return;`
    - Required: `if (cond) { return; }`
- **Method parameter wrapping**: when parameters must span multiple lines, keep the opening parenthesis on the same line and put each parameter on its own line with double indentation.
  ```java
  // correct
  private int layoutParagraphOnCurrentPage(
          ParagraphNode paragraphNode,
          int startIndex,
          LayoutContext context) {

  // wrong — aligned after the opening parenthesis
  private int layoutParagraphOnCurrentPage(ParagraphNode paragraphNode,
                                           int startIndex,
                                           LayoutContext context) {
  ```
- **Right after a method declaration**: **[important]** no blank line after the opening brace. Start the body **immediately**.
  ```java
  // correct
  private void renderChildren(Frame frame, float absX) {
      float currentX = 0;
  }

  // wrong
  private void renderChildren(Frame frame, float absX) {

      float currentX = 0;
  }
  ```
- **Vertical whitespace (blank lines)**:
    - **Right after a class declaration**: no blank line.
    - **Before `return`**: always a blank line. **Exception**: unnecessary when a control block contains only the `return`.
    - **Before `break` in a switch-case**: always a blank line when the case block contains logic.
    - **Around control statements**: always a blank line before and after `if`, `while`, `for`, `switch`, etc. **Exception**: unnecessary when the control statement is the method's only statement (excluding a trailing return/throw).
- **Lambdas**: single-line lambdas use the concise form (omit `{}` and `;`).
    - Example: `list.forEach(item -> process(item));`
    - Forbidden: `list.forEach(item -> { process(item); });`
- **toString()**: `ClassName{key=value, ...}` format.
- **Imports**: **always** use imports instead of fully-qualified names.
- **Minimize else**: avoid `else`/`else if`; keep the happy path flat with **guard clauses** (early `return`/`break`/`continue`).
- **No double negation**: `!isNotEmpty()` is forbidden. Simple negation (`!isEmpty()`) is allowed.

## 3. Preferred Java patterns

- **Records first**: DTOs, value objects, MCP tool inputs/outputs, and `@ConfigurationProperties` are written as **records**. Immutability is the default and it is terser than Lombok.
- **Prefer lambdas**: lambdas over anonymous classes. Actively use functional interfaces (`Function`, `Predicate`, `Consumer`, `Supplier`, ...).
- **No Lombok** (2026-08-20 — aligned with actual practice; no module uses it): records and explicit constructors already cover what Lombok would generate. Do not add the dependency.
    - JPA entities: protected no-arg constructor + **no setters** — state changes go through intent-revealing methods (`updateProgress(int rate)`).
- **Immutability**: fields are `final` by default. Remove `final` only when mutability is explicitly needed.

## 4. Spring rules

- **Dependency injection**: constructor injection only (explicit constructor + `final` fields). Field `@Autowired` is forbidden.
- **Use Boot-managed beans**: beans Boot auto-configures (`ObjectMapper`, `Clock`, ...) are injected, never instantiated inline — a hand-made instance silently drops the modules and settings Boot registered. Before `new`-ing a framework type, check whether a bean already exists.
- **Caller identity in one place**: controllers obtain the authenticated caller via `@AuthenticationPrincipal` or a custom `HandlerMethodArgumentResolver` — hand-parsing `authentication.getName()` in each controller is forbidden (repetition is the smell).
- **Token claim validation belongs to the decoder**: compose `OAuth2TokenValidator`s (`DelegatingOAuth2TokenValidator`) and attach them where the decoder is built. Decode-then-hand-check-claims with if-statements is forbidden — one codebase, one validation style.
- **`@Transactional`**: on application services only (never controllers or repositories). Query services default to `@Transactional(readOnly = true)`.
- **Never expose entities**: REST controllers and MCP adapters never return entities. Convert to record DTOs in the application layer. **MCP tool output feeds straight into the LLM** — include only the fields needed, and never accidentally ship internal identifiers or sensitive fields.
- **Exception → HTTP mapping**: throw `ApiException` subtypes and map them in one place via `@RestControllerAdvice` into the §7 error envelope (`ErrorResponse` — `{error:{code,message,field,traceId}}`; the security chain's 401 writes the same envelope). MCP adapters use the same shared mapping (no ad-hoc try-catch conversion inside individual tools).

  | Situation | HTTP | MCP tool error message direction |
  |------|------|--------------------------|
  | Auth failure / audience mismatch | 401 | (blocked by the security chain) |
  | Unauthorized **write** attempt | 403 | "담당자만 가능" |
  | Resource missing **or query outside visibility** | 404 | "해당 데이터 없음" — **conceal existence itself** |
  | Optimistic lock conflict | 409 | prompt re-reading the latest values |
  | Input **format** violation (`@Valid` failure) | 400 | `VALIDATION_ERROR` — name the field |
  | Input **semantic/rule** violation (unknown enum value, role invariants, fixed targets) | 422 | explain the parameter error |

    - **404 concealment principle**: a resource the requester may not view returns 404, not 403 — "does not exist" and "cannot see" must be indistinguishable. This judgment lives inside application services (structural principle 3).
- **Input validation**: request DTOs are validated declaratively with jakarta validation (`@Valid`, `@NotNull`, ...); failures map to **400 `VALIDATION_ERROR`** (PRD-pms §7). Semantic rule violations the annotations cannot express map to 422.
- **traceId must trace**: the error envelope's `traceId` is written to the server log together with the failure it identifies — an identifier the user can report but no one can correlate with a log line is forbidden.
- **Config binding**: prefer `@ConfigurationProperties` (record). `@Value` only for single values.

## 5. Spring Modulith package structure (`pms/` app)

> Applies to `pms/`. The `host/` app is a thin agent-loop application — its
> internal structure is decided at M0.
> Module list **confirmed at M0 scaffolding (2026-08-17, shared decision log)**:
> identity · project · resource · maintenance · notification · common.
> Supporting-module candidates chat(BFF) · mcpconfig are deferred (revisit at M1);
> the `/mcp` adapter module is added by the MCP dev when promoting the mock.

```
kr.proten.pms
└── project/                  # module root = public API (only what other modules may reference)
    ├── ProjectApi.java       #   public service interfaces, public DTOs, domain events
    └── internal/             # no references from outside the module (verified by Modulith)
        ├── application/      #   use cases, transactions, visibility/404-concealment rules
        ├── domain/           #   entities, VOs, domain services, repository interfaces
        └── web/              #   REST controllers + MCP adapters (siblings)
```

- **Inter-module communication**: call the other module's root public API or publish events. **Direct access to another module's repositories/internal is forbidden.**
- These boundaries are enforced by the Modulith/ArchUnit tests — when a test breaks, fix the structure, not the test.

## 6. Code quality

- Remove unused variables, imports, and methods
- **Ask the DB, don't load-and-scan**: existence/count questions go through derived queries (`existsBy...`, `count()`), never `findAll()` + `isEmpty()`/`size()`. Deliberate load-all + in-memory filtering is allowed only with an ASSUMPTION comment stating the scale rationale and the revisit trigger.
- No duplicated code; minimize cognitive complexity
- No empty catch blocks (at minimum log or leave an intent comment)
- No raw types (specify generic types); compare strings with `equals()`
- Logger instead of `System.out.println`; resources via try-with-resources
- **Never log**: Authorization headers / raw tokens, personally identifiable information (resident registration numbers, salaries, ...), or full raw user questions / DB text. If needed, log only IDs or lengths. Keeping raw text for audit purposes is the job of AuditLog (M2), not application logs.

## 7. SOLID · DDD

- **SOLID**: single responsibility (one reason to change) · open for extension, closed for modification · subtypes honor the supertype contract · small, segregated interfaces · depend on abstractions, not concretions.
- **DDD**:
    - **Ubiquitous Language**: class and method names reflect domain terms as-is (가동률/utilization, 과부하/overbooked, 배정/assignment).
    - **Entity vs Value Object**: distinguished by identity → Entity; compared by value → VO (record).
    - **Aggregate**: group related objects into one unit and access them only through the aggregate root.
    - **Repository**: abstracts persistence. The domain layer knows nothing about storage.
    - **Domain Service**: the home of domain logic that belongs to neither an entity nor a VO.
    - **Application Service**: orchestrates use cases and manages transactions. Contains no domain logic. **In this project, visibility, permissions, and 404 concealment are this layer's responsibility.**

## 8. TDD (Test-Driven Development)

- **Red → Green → Refactor**: write a failing test before the production code. Production code written without a test does not count as done.
    1. **Red**: write a failing test that verifies the requirement, and confirm it fails.
    2. **Green**: write only the minimum code that makes the test pass. Do not generalize early.
    3. **Refactor**: improve structure only while tests are green (remove duplication, clean up names).
- **Unit of work**: 1 ROADMAP checklist item = start with a test list → digest it one Red-Green-Refactor cycle at a time.
- **Test priority**:
    - **Application services**: mandatory. Visibility/permission/404-concealment rules live here, so include cases per role (division head / team lead / member / admin).
    - **MCP tools**: slice tests for tool registration → application delegation → error mapping (403/404/409/422).
    - **Domain**: entity/VO invariants via unit tests.
    - Do not test getters/setters or trivial delegation.
- **Test style**:
    - Names: `methodName_condition_expectedResult`, or Korean via `@DisplayName`.
    - Structure: separate with Given-When-Then comments.
    - Unit tests isolate collaborators with Mockito; keep boundary tests (Modulith/ArchUnit) and integration tests separate.
- **Integration test DB**: anything that touches SQL dialect runs on Testcontainers (PostgreSQL) — an H2 pass there is meaningless. Dialect-independent semantics (auth flow, error envelope shape) may run on H2 for speed; state that rationale in the test's class comment (2026-08-20 — codifies the M1a/M1b practice).
- **Link to verification gates**: `bash scripts/verify.sh` runs the tests accumulated through TDD. Writing tests in a batch afterwards hollows out this gate and is forbidden.

## 9. Example

```java
/**
 * 프로젝트 조회 유스케이스.
 * 가시성 규칙(본부장=본부, 팀장=팀, 팀원=본인 참여)과 404 은닉이 이 계층에 있다.
 */
@Service
@Transactional(readOnly = true)
public class ProjectQueryService {
    private final ProjectRepository projectRepository;
    private final VisibilityPolicy visibilityPolicy;

    public ProjectQueryService(ProjectRepository projectRepository, VisibilityPolicy visibilityPolicy) {
        this.projectRepository = projectRepository;
        this.visibilityPolicy = visibilityPolicy;
    }

    /**
     * 프로젝트 단건을 조회합니다.
     * 가시성 밖 프로젝트는 존재를 숨기기 위해 403이 아닌 404로 응답합니다.
     */
    public ProjectDetail getProject(Long projectId, RequesterContext requester) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (!visibilityPolicy.canView(requester, project)) {
            throw new ProjectNotFoundException(projectId);
        }

        return ProjectDetail.from(project);
    }
}
```

```java
/**
 * 프로젝트 상세 응답. MCP 도구를 거쳐 LLM에 전달되므로 필요한 필드만 담는다.
 */
public record ProjectDetail(Long id, String name, ProjectStatus status, int progressRate) {

    public static ProjectDetail from(Project project) {
        return new ProjectDetail(
                project.getId(),
                project.getName(),
                project.getStatus(),
                project.getProgressRate());
    }
}
```
