package kr.proten.pms.common.internal.web;

import java.security.Principal;
import java.util.List;
import kr.proten.pms.common.CallerPersonId;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link CallerPersonId} 리졸버 등록 — 보안 체인이 인증을 보장한 뒤에만 도달하므로
 * principal 부재·sub 비숫자는 요청 오류가 아니라 구성 결함으로 취급한다(즉시 실패).
 */
@Configuration
class CallerPersonIdWebConfig implements WebMvcConfigurer {
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CallerPersonIdArgumentResolver());
    }

    private static class CallerPersonIdArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CallerPersonId.class)
                    && Long.class.equals(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            Principal principal = webRequest.getUserPrincipal();
            if (principal == null) {
                throw new IllegalStateException(
                        "@CallerPersonId는 인증 필수 라우트에서만 사용할 수 있습니다");
            }

            try {
                return Long.valueOf(principal.getName());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                        "토큰 sub가 personId(Long) 규약과 다릅니다: " + principal.getName(), e);
            }
        }
    }
}
