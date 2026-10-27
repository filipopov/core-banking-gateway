package io.github.filipopov.gateway.api.common;

import io.github.filipopov.gateway.application.tenant.TenantId;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One OpenAPI document per API version: {@code /v3/api-docs/v1} and {@code /v3/api-docs/v2}. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    static {
        // TenantId is resolved from a header by TenantArgumentResolver; document the header instead of the type.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(TenantId.class);
    }

    @Bean
    OpenAPI gatewayOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Core Banking Gateway")
                .description("One stable API in front of several core banking providers. "
                        + "Errors are RFC 9457 application/problem+json documents.")
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }

    @Bean
    GroupedOpenApi v1Api() {
        return GroupedOpenApi.builder()
                .group("v1")
                .displayName("API v1 (legacy)")
                .pathsToMatch("/api/v1/**")
                .addOperationCustomizer(tenantHeader())
                .build();
    }

    @Bean
    GroupedOpenApi v2Api() {
        return GroupedOpenApi.builder()
                .group("v2")
                .displayName("API v2 (current)")
                .pathsToMatch("/api/v2/**")
                .addOperationCustomizer(tenantHeader())
                .build();
    }

    private static OperationCustomizer tenantHeader() {
        return (operation, handlerMethod) -> operation.addParametersItem(new HeaderParameter()
                .name(TenantHeader.NAME)
                .required(true)
                .description("Tenant (bank) identifier, e.g. alpine-bank")
                .schema(new StringSchema().example("alpine-bank")));
    }
}
