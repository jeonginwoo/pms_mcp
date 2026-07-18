# Java / Spring Boot Conventions

> Scope: both Boot apps — the PMS (with the embedded MCP server) and the AI host. This file is auto-injected every session via CLAUDE.md, so it contains only rules that are "always true".

## 1. Comments

- **Classes & methods**: Javadoc `/** ... */`. Explain purpose and intent.
    - Use `@param`/`@return` only when they remove ambiguity.
- **Fields**: single-line `//` comment.
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
- **Lombok is conditional**:
    - Recommended: `@Getter`, `@RequiredArgsConstructor`, `@Builder`, `@Slf4j`
    - JPA entities: `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + **no `@Setter`** — state changes go through intent-revealing methods (`updateProgress(int rate)`).
    - Forbidden: `@Data`. `@Setter`/`@AllArgsConstructor` conflict with the immutability principle — do not use without a clear reason.
- **Immutability**: fields are `final` by default. Remove `final` only when mutability is explicitly needed.

## 4. Spring rules

- **Dependency injection**: constructor injection only (`@RequiredArgsConstructor` + `final` fields). Field `@Autowired` is forbidden.
- **`@Transactional`**: on application services only (never controllers or repositories). Query services default to `@Transactional(readOnly = true)`.
- **Never expose entities**: REST controllers and MCP adapters never return entities. Convert to record DTOs in the application layer. **MCP tool output feeds straight into the LLM** — include only the fields needed, and never accidentally ship internal identifiers or sensitive fields.
- **Exception → HTTP mapping**: throw domain exceptions and map them in one place via `@RestControllerAdvice` + `ProblemDetail`. MCP adapters use the same shared mapping module (no ad-hoc try-catch conversion inside individual tools).

  | Situation | HTTP | MCP tool error message direction |
  |------|------|--------------------------|
  | Auth failure / audience mismatch | 401 | (blocked by the security chain) |
  | Unauthorized **write** attempt | 403 | "담당자만 가능" |
  | Resource missing **or query outside visibility** | 404 | "해당 데이터 없음" — **conceal existence itself** |
  | Optimistic lock conflict | 409 | prompt re-reading the latest values |
  | Input validation failure | 422 | explain the parameter error |

    - **404 concealment principle**: a resource the requester may not view returns 404, not 403 — "does not exist" and "cannot see" must be indistinguishable. This judgment lives inside application services (structural principle 3).
- **Input validation**: request DTOs are validated declaratively with jakarta validation (`@Valid`, `@NotNull`, ...); failures map to 422.
- **Config binding**: prefer `@ConfigurationProperties` (record). `@Value` only for single values.

## 5. Spring Modulith package structure

Modules: identity · project · resource · maintenance · notification · common + supporting modules chat(BFF) · mcpconfig.

```
com.proten.pms
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
- **Integration test DB**: Testcontainers (PostgreSQL). No H2 substitute — dialect differences make a pass meaningless.
- **Link to verification gates**: `/verify`'s `./gradlew test` runs the tests accumulated through TDD. Writing tests in a batch afterwards hollows out this gate and is forbidden.

## 9. Example

```java
/**
 * 프로젝트 조회 유스케이스.
 * 가시성 규칙(본부장=본부, 팀장=팀, 팀원=본인 참여)과 404 은닉이 이 계층에 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectQueryService {
    // 프로젝트 저장소
    private final ProjectRepository projectRepository;
    // 요청자 기준 가시성 판정
    private final VisibilityPolicy visibilityPolicy;

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
