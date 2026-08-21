package kr.proten.pms.common;

import org.springframework.http.HttpStatus;

/**
 * 404 은닉 공통 예외 — 부재와 가시성 밖을 같은 응답으로 수렴시킨다
 * (구조 원칙 3 · conventions/java-spring.md §4 404 은닉 원칙 · §7 에러 표 NOT_FOUND).
 * 메시지는 정본 문구 하나로 고정한다 — 사유가 응답 형태로 새면 존재 자체가 드러난다.
 */
public class NotFoundException extends ApiException {
    public NotFoundException() {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", "해당 데이터 없음");
    }
}
