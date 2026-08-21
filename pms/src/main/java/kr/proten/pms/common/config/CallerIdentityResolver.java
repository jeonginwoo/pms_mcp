package kr.proten.pms.common.config;

import org.springframework.web.context.request.NativeWebRequest;

/**
 * 요청에서 호출자 personId를 얻는 방법 — 인증 도입 전후를 갈아 끼우는 지점.
 *
 * 구현이 둘이고 `pms.auth.enabled`가 하나를 고른다: 꺼져 있으면 헤더,
 * 켜져 있으면 토큰 subject. 컨트롤러와 서비스는 어느 쪽인지 모른다 — 그래서
 * 인증 도입이 이 패키지 안의 변경으로 끝난다(conventions §4 호출자 식별 단일 지점).
 */
public interface CallerIdentityResolver {

    /** 호출자 personId. 식별할 수 없으면 401로 수렴한다. */
    long resolve(NativeWebRequest request);
}
