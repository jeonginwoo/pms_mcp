package kr.proten.pms.person.controller.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 조직 노드의 상위 조직 변경 요청 (AC E3-5).
 *
 * <p>이름이 {@code MoveOrgUnitRequest}가 아닌 이유: 그것은 <b>인원</b>의 소속 이동
 * (E1-1 {@code PUT /people/{id}/org-unit})이 이미 쓰고 있다. 옮기는 대상이 사람인지
 * 노드인지가 요청 이름에서 갈려야 한다.
 *
 * <p>{@code null}을 허용하지 않는다: 부모 없는 노드는 회사(root)뿐이고 그것은 하나여야
 * 하므로(E3-1), 비워 보내는 요청은 두 번째 root를 만들려는 것이다.
 */
public record ChangeParentRequest(
        @NotNull(message = "상위 조직은 필수입니다") Long parentId) {
}
