package kr.proten.pms.audit.service.impl;

import kr.proten.pms.audit.AuditSource;
import kr.proten.pms.common.config.RequestPathResolver;
import org.springframework.stereotype.Component;

/**
 * 변경이 들어온 입구 판정 (PRD-pms §4 source).
 *
 * source는 서비스가 아니라 입구가 아는 값이다 — 그래서 유스케이스 파라미터로
 * 흘리지 않고 현재 요청 경로에서 읽는다. 같은 서비스를 웹과 MCP가 함께 쓰므로
 * (US-A2) 파라미터로 만들면 입구마다 배선을 잊을 수 있다.
 *
 * 경로를 읽는 일 자체는 common이 하고(`RequestPathResolver`) 여기서는 그것을 도메인
 * 어휘로 옮기기만 한다 — audit가 독립 모듈이 되면서(2026-08-22) 서블릿 API를 직접
 * 보면 "웹 관심사는 controller와 common에만"이라는 계층 규칙에 걸린다.
 *
 * ASSUMPTION: `/mcp` 어댑터는 아직 이 앱에 없어(구조 원칙 2로 위치만 고정) 현재
 * 모든 기록은 WEB이다. 어댑터가 승격되면 배선 없이 MCP로 잡히는지 실측으로 확인한다.
 */
@Component
class AuditSourceResolver {
    // /mcp 어댑터의 고정 경로 — PMS에 내장된다(구조 원칙 2)
    private static final String MCP_PATH_PREFIX = "/mcp";

    private final RequestPathResolver requestPathResolver;

    AuditSourceResolver(RequestPathResolver requestPathResolver) {
        this.requestPathResolver = requestPathResolver;
    }

    /** 요청 밖(기동 시 적재·배치)에서 일어난 변경은 WEB으로 남는다. */
    AuditSource current() {
        return requestPathResolver.current()
                .filter(path -> path.startsWith(MCP_PATH_PREFIX))
                .map(path -> AuditSource.MCP)
                .orElse(AuditSource.WEB);
    }
}
