package kr.proten.pmsmock.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 호출자 식별 — B2-0 단계는 프로퍼티 고정(mock.caller-id).
 * B2-2에서 JWT 클레임(sub) 기반으로 교체한다 — 도구·서비스 시그니처는 그대로.
 */
@Component
public class CallerContext {

    private final int callerId;

    public CallerContext(@Value("${mock.caller-id}") int callerId) {
        this.callerId = callerId;
    }

    public int callerId() {
        return callerId;
    }
}
