package kr.proten.pms.mcp.internal;

import java.util.function.Supplier;
import kr.proten.pms.common.exception.ApiException;

/**
 * 도메인 서비스 호출 지점 — 도메인 예외를 도구 에러로 바꾸는 **유일한 통로**.
 *
 * 도구마다 try-catch를 쓰지 않기 위한 장치다(conventions §4). AOP 어드바이스를 쓰지
 * 않은 이유: 도구 빈이 프록시로 감싸이면 MCP 애노테이션 탐색이 도구를 놓칠 위험이
 * 있고, 그 실패는 "카탈로그에서 도구가 조용히 사라지는" 형태로 나타난다 —
 * 문구 정본 한 곳(ToolError.from)만 지키면 되는 일에 그 위험을 살 이유가 없다.
 */
final class ToolCalls {

    private ToolCalls() {
    }

    static <T> T translating(Supplier<T> call) {
        try {
            return call.get();
        } catch (ApiException exception) {
            throw ToolError.from(exception);
        }
    }
}
