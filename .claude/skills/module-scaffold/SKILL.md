---
name: module-scaffold
description: Use when creating a new Spring Modulith module (identity/project/resource/maintenance/notification/common, supporting modules chat/mcpconfig) or adding internal layers (application/domain/web) inside one.
---

# PMS Module Scaffold

Root package `com.proten.pms`. Every module follows this skeleton (java-spring.md §5):

```
com.proten.pms.<module>
├── <Module>Api.java     # module root = public API — public service interfaces, public DTOs, domain events only
└── internal/            # no references from outside the module (verified by Modulith)
    ├── application/     # use cases, @Transactional, visibility/permission/404 concealment
    ├── domain/          # entities, VOs, domain services, repository interfaces
    └── web/             # REST controllers + MCP adapters (siblings)
```

Rules to apply while scaffolding:

1. Inter-module communication is only via the other module's root public API or event publication. Direct access to another module's internal/repositories is forbidden.
2. DTOs and VOs are records. Cross-module references are ID values only (no entity references, no physical FKs).
3. JPA entities: `@Getter` + `@NoArgsConstructor(access = PROTECTED)`, no `@Setter` — state changes go through intent-revealing methods (`updateProgress(int rate)`). `@Enumerated(EnumType.STRING)`; entities on write paths get `@Version` (optimistic locking).
4. Application services: constructor injection (`@RequiredArgsConstructor` + final), `@Transactional` here only (queries use `readOnly = true`). Queries outside visibility are concealed with 404, not 403.
5. Domain: aggregate roots guard invariants; access goes through the root only. Write a pure unit test for each invariant first (TDD — java-spring.md §8).
6. Right after creation: confirm the Modulith/ArchUnit boundary tests still pass (`./gradlew test`). Integration tests use Testcontainers (PostgreSQL) — H2 is forbidden.
7. Javadoc/comments in Korean; formatting per docs/conventions/java-spring.md.
