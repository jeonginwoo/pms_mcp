package kr.proten.pms.common.config;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link CallerPersonId} 파라미터 리졸버 등록.
 * 실제 식별 방법은 {@link CallerIdentityResolver} 구현이 갖는다 — 인증 도입은
 * 어느 구현이 활성인지만 바꾸고 이 클래스는 그대로다.
 *
 * 두 구현을 여기서 빈으로 낸다(각자 @Component가 아니라): 웹 슬라이스 테스트는
 * WebMvcConfigurer는 포함하지만 일반 @Component는 스캔하지 않아, 따로 두면
 * 컨트롤러 테스트마다 리졸버를 수동으로 import해야 한다.
 */
@Configuration
class CallerPersonIdWebConfig implements WebMvcConfigurer {
    private final CallerIdentityResolver callerIdentityResolver;

    CallerPersonIdWebConfig(CallerIdentityResolver callerIdentityResolver) {
        this.callerIdentityResolver = callerIdentityResolver;
    }

    /** 인증 미사용 — 헤더로 받는다 (기본값). */
    @Bean
    @ConditionalOnProperty(name = "pms.auth.enabled", havingValue = "false", matchIfMissing = true)
    static CallerIdentityResolver headerCallerIdentityResolver() {
        return new HeaderCallerIdentityResolver();
    }

    /** 인증 사용 — 토큰 subject로 받는다. */
    @Bean
    @ConditionalOnProperty(name = "pms.auth.enabled", havingValue = "true")
    static CallerIdentityResolver tokenCallerIdentityResolver() {
        return new TokenCallerIdentityResolver();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CallerPersonIdArgumentResolver(callerIdentityResolver));
    }

    private record CallerPersonIdArgumentResolver(CallerIdentityResolver callerIdentityResolver)
            implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CallerPersonId.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return callerIdentityResolver.resolve(webRequest);
        }
    }
}
