package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Modulith 모듈 경계 검증 (PRD-pms §0 아키텍처 규칙 — 위반=실패).
 *
 * 모듈 = 메인 패키지의 직속 하위 패키지. 이번 재구축 범위는 도메인 모듈 2종
 * (person · project) + 공통 모듈 1종이며, resource·maintenance·notification과
 * /mcp 어댑터는 각 담당이 필요할 때 추가한다(2026-08-21 결정).
 *
 * 모듈 루트만 공개 API이고 internal 하위는 외부 참조 금지 — 이 테스트가 깨지면
 * 테스트가 아니라 구조를 고친다(conventions/java-spring.md §5).
 */
class ModularityTest {
    private final ApplicationModules modules = ApplicationModules.of(PmsApplication.class);

    @Test
    @DisplayName("모듈 경계 위반 0건 — 순환 참조·internal 접근 금지")
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    @DisplayName("현재 모듈은 person · project · common 3종")
    void detectsAllModules() {
        var detected = modules.stream()
                .map(module -> module.getIdentifier().toString())
                .toList();

        assertThat(detected).containsExactlyInAnyOrder("person", "project", "common");
    }
}
