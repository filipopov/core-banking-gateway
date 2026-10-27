package io.github.filipopov.gateway.api.common;

import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.domain.error.DomainValidationException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves controller parameters of type {@link TenantId} from the {@value TenantHeader#NAME} header. Whether the
 * tenant actually exists is decided by the application layer, not here.
 */
public class TenantArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return TenantId.class.equals(parameter.getParameterType());
    }

    @Override
    public TenantId resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                    NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String value = webRequest.getHeader(TenantHeader.NAME);
        if (value == null || value.isBlank()) {
            throw new MissingTenantException("The " + TenantHeader.NAME + " header is required");
        }
        try {
            return TenantId.of(value.strip());
        } catch (DomainValidationException e) {
            throw new MissingTenantException("The " + TenantHeader.NAME + " header is malformed: " + e.getMessage());
        }
    }
}
