package kr.proten.pms.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증된 호출자의 personId(Long)를 컨트롤러 파라미터로 주입한다 — 호출자 식별의
 * 단일 지점 (conventions/java-spring.md §4 "Caller identity in one place").
 * 토큰 sub=personId 규약(목업 B2-2 정합)의 해석이 컨트롤러마다 반복되지 않도록
 * 리졸버 한 곳에 둔다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CallerPersonId {
}
