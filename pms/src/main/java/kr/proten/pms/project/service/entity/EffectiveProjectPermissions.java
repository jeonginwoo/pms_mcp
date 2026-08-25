package kr.proten.pms.project.service.entity;

import java.util.List;
import java.util.Optional;

/**
 * 한 프로젝트의 <b>지금 유효한</b> 역할×기능 매트릭스 — §4-2 기본값 위에 override를
 * 겹친 결과 (US-A8).
 *
 * <p><b>병합이 여기 한 곳뿐인 것이 요점이다.</b> 읽는 쪽이 둘이기 때문이다: 쓰기
 * 판정({@code ProjectActionPermission})과 A8-1 조회 응답. 병합을 양쪽에 두면 화면이
 * "할 수 있다"고 그린 칸에서 서버가 403을 내는 어긋남이 생기고, 사용자는 그것을
 * 버그로 읽는다.
 *
 * <p>override 목록을 <b>생성 시 한 번</b> 받는다 — 칸마다 저장소를 되묻는 방식은
 * 매트릭스 한 번 그리는 데 수십 질의가 된다(conventions/java-spring.md §6).
 */
public final class EffectiveProjectPermissions {

    private final List<ProjectPermissionOverride> overrides;

    private EffectiveProjectPermissions(List<ProjectPermissionOverride> overrides) {
        this.overrides = List.copyOf(overrides);
    }

    public static EffectiveProjectPermissions of(List<ProjectPermissionOverride> overrides) {
        return new EffectiveProjectPermissions(overrides);
    }

    /** 그 칸이 지금 허용인가 */
    public boolean allows(ProjectRole role, ProjectAction action) {
        return storedValue(role, action)
                .orElseGet(() -> ProjectPermissionRules.allowedByDefault(role, action));
    }

    /** 기본값과 달라 저장된 칸인가 — A8-1의 화면 표시용 */
    public boolean isOverridden(ProjectRole role, ProjectAction action) {
        return storedValue(role, action).isPresent();
    }

    /**
     * 고정 칸의 override는 <b>무시한다</b>. 저장 경로가 422로 막지만(A8-4), DB를 직접
     * 고친 행이 판정을 뚫는 길까지 열어 둘 이유는 없다.
     */
    private Optional<Boolean> storedValue(ProjectRole role, ProjectAction action) {
        if (!ProjectPermissionRules.editable(role, action)) {
            return Optional.empty();
        }

        return overrides.stream()
                .filter(o -> o.getRole() == role && o.getAction() == action)
                .findFirst()
                .map(ProjectPermissionOverride::isAllowed);
    }
}
