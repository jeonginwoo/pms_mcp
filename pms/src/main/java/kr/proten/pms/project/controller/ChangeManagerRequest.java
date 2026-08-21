package kr.proten.pms.project.controller;

import jakarta.validation.constraints.NotNull;

/**
 * PM 교체 요청 (AC A6-1) — `PUT /projects/{id}/pm`.
 * 대상 인원과 낙관적 락 버전만 받는다: 새 PM의 배정은 없으면 서버가 만든다(A6-4).
 */
public record ChangeManagerRequest(
        @NotNull(message = "PM으로 지정할 인원 id는 필수입니다") Long personId,
        @NotNull(message = "version은 필수입니다") Long version) {
}
