package kr.proten.pms;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Modulith 모듈 경계 검증 (PRD-pms §0 아키텍처 규칙 — 위반=실패).
 *
 * 무엇을 잡는가: **모듈 간 순환 참조**와 **남의 모듈 내부 접근** 두 가지다. 이 검증이
 * 없으면 모듈 분리는 폴더 이름일 뿐이다 — project가 person의 저장소를 직접 부르는
 * 코드가 그냥 컴파일되고, 나중에 person 스키마를 바꿀 때까지 아무도 그 의존을 모른다.
 * auth를 떼어낼 때 person↔auth 순환을 즉시 잡아 `AccountPort` 역전을 만든 것이 이 테스트다.
 *
 * **모듈의 공개 API = 모듈 루트 패키지** (Modulith 기본 규약, 2026-08-22 정렬).
 * 하위 패키지(controller·service·repository)는 전부 internal이므로 밖으로 내보낼
 * 타입만 루트에 둔다 — 루트에 있는 파일 목록이 곧 그 모듈의 공개 계약이다.
 * 전에는 하위 패키지를 `@NamedInterface`(package-info.java)로 열어 뒀는데, 그러면
 * "무엇이 공개인지"가 8개 파일에 흩어지고 소비자 없는 선언이 남는다.
 *
 * `common`은 모듈이 아니라 **공용 배선**이라 검증에서 제외한다: 에러 봉투·호출자
 * 식별·페이지 표현은 모든 모듈이 쓰는 것이고, 거기에 캡슐화할 도메인이 없다.
 * 모듈로 두면 모든 모듈이 common에 의존한다는 사실만 반복해서 기록된다.
 */
class ModularityTest {
    private final ApplicationModules modules =
            ApplicationModules.of(PmsApplication.class, resideInAPackage("kr.proten.pms.common.."));

    @Test
    @DisplayName("모듈 경계 위반 0건 — 순환 참조·internal 접근 금지")
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    @DisplayName("현재 모듈은 person · auth · project · resource · notification · audit · mcp 7종")
    void detectsAllModules() {
        var detected = modules.stream()
                .map(module -> module.getIdentifier().toString())
                .toList();

        assertThat(detected).containsExactlyInAnyOrder(
                "person", "auth", "project", "resource", "notification", "audit", "mcp");
    }
}
