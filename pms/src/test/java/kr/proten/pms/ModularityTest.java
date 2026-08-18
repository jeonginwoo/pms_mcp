package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Modulith 모듈 경계 검증 (PRD-pms §0 아키텍처 규칙 — 위반=실패).
 * 모듈 = 메인 패키지의 직속 하위 패키지 — §3 확정 6종(identity · project ·
 * resource · maintenance · notification · common) + /mcp 어댑터 모듈 mcp
 * (MCP 담당, 2026-08-17 모듈 결정이 예정한 추가).
 * 모듈 루트만 공개 API이고 하위 패키지는 외부 참조 금지 — 이 테스트가 깨지면
 * 테스트가 아니라 구조를 고친다(conventions/java-spring.md §5).
 */
class ModularityTest {
    // 메인 클래스 기준으로 도출한 모듈 모델 (기본 감지 전략: 직속 하위 패키지)
    private final ApplicationModules modules = ApplicationModules.of(PmsApplication.class);

    @Test
    @DisplayName("모듈 경계 위반 0건 — 순환 참조·internal 접근 금지")
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    @DisplayName("확정 모듈 6종 + /mcp 어댑터 모듈이 전부 감지된다")
    void detectsAllModules() {
        var detected = modules.stream()
                .map(module -> module.getIdentifier().toString())
                .toList();

        // mcp = 임베디드 /mcp 어댑터 (MCP 담당 — 2026-08-17 모듈 결정이 예정한 추가분)
        assertThat(detected).containsExactlyInAnyOrder(
                "identity",
                "project",
                "resource",
                "maintenance",
                "notification",
                "common",
                "mcp");
    }
}
