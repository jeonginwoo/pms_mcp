package com.proten.pms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Spring Modulith 모듈 경계 검증 — 첫날부터 통과 상태를 유지해야 한다.
 */
class ModularityTests {
    private final ApplicationModules modules = ApplicationModules.of(PmsApplication.class);

    @Test
    @DisplayName("모듈 경계 규칙(internal 비참조 등)을 위반하지 않는다")
    void verifiesModularStructure() {
        modules.verify();
    }
}
