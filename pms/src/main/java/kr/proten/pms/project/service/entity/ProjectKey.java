package kr.proten.pms.project.service.entity;

import java.util.Locale;
import kr.proten.pms.common.exception.ValidationException;

/**
 * 프로젝트 식별 이름 — 고객사와 프로젝트명의 쌍.
 *
 * 중복 판정(AC A1-2) 정규화 규칙의 유일 지점이다: trim · 연속 공백 1개로 축약 ·
 * 영문 대소문자 무시. 규칙이 두 곳에 생기면 저장된 정규화 값과 질의 조건이
 * 어긋나 중복을 놓친다.
 */
public record ProjectKey(String client, String name) {

    public ProjectKey {
        requireText(client, "client");
        requireText(name, "name");
    }

    /** 중복 판정용 고객사 값. */
    public String normalizedClient() {
        return normalize(client);
    }

    /** 중복 판정용 프로젝트명 값. */
    public String normalizedName() {
        return normalize(name);
    }

    private static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("필수 입력값입니다", field);
        }
    }
}
