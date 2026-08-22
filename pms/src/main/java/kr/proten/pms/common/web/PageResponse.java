package kr.proten.pms.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * §7 목록 page 봉투 — {content, page, size, totalElements, totalPages}.
 *
 * 스프링의 Page를 그대로 직렬화하지 않는 이유: 그 JSON 형태는 프레임워크 버전에
 * 따라 바뀌는 내부 표현이라 §7이 못 박은 계약을 보장하지 못한다.
 * 2026-08-22에 common으로 승격했다 — 감사 조회 뷰·알림 목록이 같은 봉투를 쓰면서
 * "두 번째 모듈이 페이징을 시작하면" 조건이 성립했다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
