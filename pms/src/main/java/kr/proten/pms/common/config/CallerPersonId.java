package kr.proten.pms.common.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 호출자의 personId를 컨트롤러 파라미터로 주입한다 — 호출자 식별의 단일 지점
 * (conventions/java-spring.md §4 "Caller identity in one place").
 *
 * 지금 출처는 {@code X-Caller-Person-Id} 헤더다(인증 미도입 — 2026-08-21 결정).
 * 인증이 들어오면 리졸버 한 클래스만 토큰 subject를 읽도록 바꾸면 되고, 컨트롤러와
 * 서비스는 그대로다 — 그래서 컨트롤러마다 헤더를 직접 읽지 않는다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CallerPersonId {
}
