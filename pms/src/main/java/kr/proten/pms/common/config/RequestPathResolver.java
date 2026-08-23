package kr.proten.pms.common.config;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 현재 요청의 경로 — 요청 밖(기동 시 적재·스케줄러)에서는 비어 있다.
 *
 * 호출자 식별(`CallerIdentityResolver`)과 같은 자리에 두는 이유: 둘 다 "지금 이
 * 요청에서만 알 수 있는 것"을 읽는 배선이고, 그런 웹 접점은 common에 모아 둔다.
 * 도메인 모듈이 직접 `RequestContextHolder`를 보면 서블릿 API가 서비스 계층까지
 * 번져 `LayerRuleTest`가 막는다 — 그 규칙을 우회하지 않고 경계를 여기로 옮긴다.
 */
@Component
public class RequestPathResolver {

    public Optional<String> current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }

        return Optional.ofNullable(servletAttributes.getRequest().getRequestURI());
    }
}
