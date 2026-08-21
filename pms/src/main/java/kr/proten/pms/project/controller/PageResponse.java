package kr.proten.pms.project.controller;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * §7 목록 page 봉투 — {content, page, size, totalElements, totalPages}.
 *
 * 스프링의 Page를 그대로 직렬화하지 않는 이유: 그 JSON 형태는 프레임워크 버전에
 * 따라 바뀌는 내부 표현이라 §7이 못 박은 계약을 보장하지 못한다.
 * 두 번째 모듈이 페이징을 시작하면 common으로 승격한다.
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
