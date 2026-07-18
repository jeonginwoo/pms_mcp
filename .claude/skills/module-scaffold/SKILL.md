---
name: module-scaffold
description: Spring Modulith 모듈(identity/project/resource/maintenance/notification/common, 지원 모듈 chat/mcpconfig)을 새로 만들거나 모듈 안에 internal 레이어(application/domain/web)를 추가할 때 사용.
---

# PMS 모듈 스캐폴드

루트 패키지 `com.proten.pms`. 모든 모듈은 이 골격을 따른다 (java-spring.md §5):

```
com.proten.pms.<module>
├── <Module>Api.java     # 모듈 루트 = 공개 API — 공개 서비스 인터페이스·공개 DTO·도메인 이벤트만
└── internal/            # 모듈 밖 참조 금지 (Modulith가 검증)
    ├── application/     # 유스케이스·@Transactional·가시성/권한/404 은닉
    ├── domain/          # 엔티티·VO·도메인 서비스·리포지토리 인터페이스
    └── web/             # REST 컨트롤러 + MCP 어댑터 (형제 관계)
```

스캐폴딩 시 적용할 규칙:

1. 모듈 간 통신은 상대 모듈 루트의 공개 API 호출 또는 이벤트 발행뿐. 다른 모듈의 internal·리포지토리 직접 접근 금지.
2. DTO·VO는 record. 다른 모듈 참조는 ID 값으로만 (엔티티 참조·물리 FK 금지).
3. JPA 엔티티: `@Getter` + `@NoArgsConstructor(access = PROTECTED)`, `@Setter` 금지 — 상태 변경은 의도가 드러나는 메서드로(`updateProgress(int rate)`). `@Enumerated(EnumType.STRING)`, 쓰기 경로 엔티티는 `@Version`(낙관적 락).
4. application 서비스: 생성자 주입(`@RequiredArgsConstructor` + final), `@Transactional`은 여기만 (조회는 `readOnly = true`). 가시성 밖 조회는 403이 아니라 404로 은닉.
5. domain: 애그리거트 루트가 불변식을 지키고, 접근은 루트를 통해서만. 불변식마다 순수 단위 테스트를 먼저 작성한다 (TDD — java-spring.md §8).
6. 생성 직후: Modulith/ArchUnit 경계 테스트가 여전히 통과하는지 확인 (`./gradlew test`). 통합 테스트는 Testcontainers(PostgreSQL) — H2 금지.
7. Javadoc·주석은 한국어, 포매팅은 docs/conventions/java-spring.md.
