# Java / Spring Boot 컨벤션

> 대상: PMS(MCP 서버 내장)와 AI 호스트, 두 Boot 앱. 이 파일은 CLAUDE.md를 통해 매 세션 자동 주입되므로 "항상 참인 규칙"만 담는다.

## 1. 주석

- **클래스·메서드**: Javadoc `/** ... */`. 목적과 의도를 설명한다.
    - `@param`/`@return`은 모호함을 없앨 때만 쓴다.
- **필드**: 한 줄 주석 `//`.
- **HTML 태그 금지**: Javadoc에 `<ul>`, `<p>`, `<code>` 등 금지. 목록은 `-`, 문단 구분은 빈 줄로.
- **예외**: 기본 생성자·getter·setter에는 주석을 달지 않는다.

## 2. 포매팅

- **중괄호**: **항상** 사용한다.
    - 금지: `if (cond) return;`
    - 필수: `if (cond) { return; }`
- **메서드 파라미터 줄바꿈**: 여러 줄로 나눠야 하면 여는 괄호는 같은 줄에 두고, 각 파라미터를 이중 들여쓰기로 한 줄씩.
  ```java
  // 올바름
  private int layoutParagraphOnCurrentPage(
          ParagraphNode paragraphNode,
          int startIndex,
          LayoutContext context) {

  // 잘못됨 — 여는 괄호 뒤에 정렬
  private int layoutParagraphOnCurrentPage(ParagraphNode paragraphNode,
                                           int startIndex,
                                           LayoutContext context) {
  ```
- **메서드 선언 직후**: **[중요]** 여는 중괄호 다음에 빈 줄을 넣지 않는다. 본문을 **즉시** 시작한다.
  ```java
  // 올바름
  private void renderChildren(Frame frame, float absX) {
      float currentX = 0;
  }

  // 잘못됨
  private void renderChildren(Frame frame, float absX) {

      float currentX = 0;
  }
  ```
- **수직 여백 (빈 줄)**:
    - **클래스 선언 직후**: 빈 줄을 넣지 않는다.
    - **`return` 앞**: 항상 빈 줄. **예외**: 제어 블록 안에 `return` 하나만 있으면 불필요.
    - **switch-case의 `break` 앞**: case 블록에 로직이 있으면 항상 빈 줄.
    - **제어문 앞뒤**: `if`, `while`, `for`, `switch` 등의 앞뒤에 항상 빈 줄. **예외**: 제어문이 메서드의 유일한 문장(마지막 return/throw 제외)이면 불필요.
- **람다**: 한 줄 람다는 간결 문법 (`{}`와 `;` 생략).
    - 예: `list.forEach(item -> process(item));`
    - 금지: `list.forEach(item -> { process(item); });`
- **toString()**: `ClassName{key=value, ...}` 형식.
- **import**: 풀 패키지명 대신 **항상** import를 쓴다.
- **else 최소화**: `else`/`else if`를 피하고 **Guard Clause**(이른 `return`/`break`/`continue`)로 happy path를 평평하게 유지한다.
- **이중 부정 금지**: `!isNotEmpty()` 금지. 단순 부정(`!isEmpty()`)은 허용.

## 3. Java 선호 패턴

- **record 우선**: DTO·Value Object·MCP 도구 입출력·`@ConfigurationProperties`는 **record**로 작성한다. 불변이 기본값이고 Lombok보다 간결하다.
- **람다 선호**: 익명 클래스 대신 람다. 함수형 인터페이스(`Function`, `Predicate`, `Consumer`, `Supplier` 등)를 적극 활용한다.
- **Lombok은 조건부**:
    - 권장: `@Getter`, `@RequiredArgsConstructor`, `@Builder`, `@Slf4j`
    - JPA 엔티티: `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + **`@Setter` 금지** — 상태 변경은 의도가 드러나는 메서드로 (`updateProgress(int rate)`).
    - 금지: `@Data`. `@Setter`·`@AllArgsConstructor`는 불변성 원칙과 충돌하므로 명확한 사유 없이 쓰지 않는다.
- **불변성**: 필드는 기본 `final`. 가변이 명시적으로 필요할 때만 제거한다.

## 4. Spring 규칙

- **의존성 주입**: 생성자 주입만 허용 (`@RequiredArgsConstructor` + `final` 필드). 필드 `@Autowired` 금지.
- **`@Transactional`**: application 서비스에만 붙인다 (컨트롤러·리포지토리 금지). 조회 서비스는 `@Transactional(readOnly = true)` 기본.
- **엔티티 노출 금지**: REST 컨트롤러·MCP 어댑터는 엔티티를 반환하지 않는다. application 계층에서 record DTO로 변환한다. **MCP 도구 출력은 그대로 LLM 입력이 된다** — 필요한 필드만 담고, 내부 식별자·민감 필드를 실수로 싣지 않는다.
- **예외 → HTTP 매핑**: 도메인 예외를 던지고 `@RestControllerAdvice` + `ProblemDetail`에서 한 곳에 매핑한다. MCP 어댑터도 같은 매핑 공통 모듈을 사용한다 (개별 도구에서 try-catch로 임의 변환 금지).

  | 상황 | HTTP | MCP 도구 에러 메시지 방향 |
  |------|------|--------------------------|
  | 인증 실패·audience 불일치 | 401 | (보안 체인에서 차단) |
  | 권한 없는 **쓰기** 시도 | 403 | "담당자만 가능" |
  | 리소스 없음 **또는 가시성 밖 조회** | 404 | "해당 데이터 없음" — **존재 자체를 은닉** |
  | 낙관적 락 충돌 | 409 | 최신값 재조회 유도 |
  | 입력 검증 실패 | 422 | 파라미터 오류 안내 |

    - **404 은닉 원칙**: 조회 권한이 없는 리소스는 403이 아니라 404 — "없다"와 "못 본다"를 구분할 수 없게 한다. 이 판정은 application 서비스 안에 있다 (구조적 원칙 3).
- **입력 검증**: 요청 DTO는 jakarta validation(`@Valid`, `@NotNull` 등)으로 선언적으로 검증하고, 실패는 422로 매핑한다.
- **설정 바인딩**: `@ConfigurationProperties`(record) 우선. `@Value`는 단일 값에만.

## 5. Spring Modulith 패키지 구조

모듈: identity · project · resource · maintenance · notification · common + 지원 모듈 chat(BFF) · mcpconfig.

```
com.proten.pms
└── project/                  # 모듈 루트 = 공개 API (외부 모듈이 참조 가능한 것만)
    ├── ProjectApi.java       #   공개 서비스 인터페이스·공개 DTO·도메인 이벤트
    └── internal/             # 모듈 밖 참조 금지 (Modulith가 검증)
        ├── application/      #   유스케이스·트랜잭션·가시성/404 은닉 규칙
        ├── domain/           #   엔티티·VO·도메인 서비스·리포지토리 인터페이스
        └── web/              #   REST 컨트롤러 + MCP 어댑터 (형제 관계)
```

- **모듈 간 통신**: 상대 모듈의 루트 공개 API 호출 또는 이벤트 발행. **다른 모듈의 리포지토리·internal 직접 접근 금지.**
- 이 경계는 Modulith/ArchUnit 테스트가 검증한다 — 테스트가 깨지면 구조를 고치는 것이지 테스트를 고치는 것이 아니다.

## 6. 코드 품질

- 사용하지 않는 변수·import·메서드 제거
- 중복 코드 금지, 인지 복잡도(Cognitive Complexity) 최소화
- 빈 catch 블록 금지 (최소한 로깅 또는 의도 주석)
- raw type 금지 (제네릭 타입 명시), 문자열 비교는 `equals()`
- `System.out.println` 대신 logger, 자원은 try-with-resources
- **로그 금지 항목**: Authorization 헤더·토큰 원문, 개인 식별 정보(주민번호·연봉 등), 사용자 질문·DB 텍스트 원문 전체. 필요하면 ID나 길이만 남긴다. 감사 목적 원문 보관은 AuditLog(M2)의 몫이지 애플리케이션 로그가 아니다.

## 7. SOLID · DDD

- **SOLID**: 단일 책임(변경 이유 하나) · 확장에 열리고 수정에 닫힘 · 하위 타입은 상위 계약 준수 · 인터페이스는 작게 분리 · 구체가 아닌 추상에 의존.
- **DDD**:
    - **Ubiquitous Language**: 클래스·메서드 이름은 도메인 용어(가동률, 과부하, 배정)를 그대로 반영한다.
    - **Entity vs Value Object**: 식별자로 구별되면 Entity, 값으로 비교되면 VO(record).
    - **Aggregate**: 관련 객체를 하나의 단위로 묶고 Aggregate Root를 통해서만 접근한다.
    - **Repository**: 영속성을 추상화한다. 도메인 계층은 저장 방식을 모른다.
    - **Domain Service**: Entity·VO에 속하지 않는 도메인 로직의 자리.
    - **Application Service**: 유스케이스 조율·트랜잭션 관리. 도메인 로직을 담지 않는다. **이 프로젝트에서는 가시성·권한·404 은닉이 이 계층의 책임이다.**

## 8. TDD (Test-Driven Development)

- **Red → Green → Refactor**: 프로덕션 코드보다 실패하는 테스트를 먼저 작성한다. 테스트 없이 작성된 프로덕션 코드는 완료로 인정하지 않는다.
    1. **Red**: 요구사항을 검증하는 실패 테스트를 먼저 작성하고, 실패를 확인한다.
    2. **Green**: 테스트를 통과시키는 최소한의 코드만 작성한다. 미리 일반화하지 않는다.
    3. **Refactor**: 테스트가 초록인 상태에서만 구조를 개선한다 (중복 제거, 이름 정리).
- **작업 단위**: ROADMAP 체크 항목 1개 = 테스트 목록부터 작성 → 하나씩 Red-Green-Refactor로 소화.
- **테스트 우선순위**:
    - **Application Service**: 필수. 가시성·권한·404 은닉 규칙이 여기 있으므로 권한별(본부장/팀장/팀원/관리자) 케이스를 반드시 포함.
    - **MCP 도구**: 도구 등록 → application 위임 → 에러 매핑(403/404/409/422)을 슬라이스 테스트로 검증.
    - **Domain**: Entity·Value Object의 불변식은 단위 테스트로.
    - Getter/Setter·단순 위임은 테스트하지 않는다.
- **테스트 스타일**:
    - 이름: `메서드명_조건_기대결과` 또는 한국어 `@DisplayName` 사용.
    - 구조: Given-When-Then 주석으로 구분.
    - 단위 테스트는 Mockito로 협력자를 격리하고, 경계 테스트(Modulith/ArchUnit)와 통합 테스트는 별도 유지.
- **통합 테스트 DB**: Testcontainers(PostgreSQL)를 사용한다. H2 대체 금지 — 방언 차이로 통과가 무의미해진다.
- **검증 게이트와 연결**: `/verify`의 `./gradlew test`는 TDD로 쌓인 테스트가 실행되는 것이다. 테스트를 나중에 몰아 쓰는 방식은 이 게이트를 형식화시키므로 금지.

## 9. 예제

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
