package com.wutsi.ndopify.config

import com.wutsi.ndopify.common.dto.HttpHeader
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.GlobalOperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class OpenApiConfiguration {
    companion object {
        const val BEARER_AUTH = "bearerAuth"
    }

    // Adds the "Authorize" button to Swagger UI: the JWT entered there is sent as "Authorization: Bearer <jwt>"
    @Bean
    open fun openApi(): OpenAPI {
        return OpenAPI()
            .components(
                Components().addSecuritySchemes(
                    BEARER_AUTH,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token returned by POST /v1/auth")
                )
            )
            .addSecurityItem(SecurityRequirement().addList(BEARER_AUTH))
    }

    @Bean
    open fun tenantIdHeaderCustomizer(): GlobalOperationCustomizer {
        return GlobalOperationCustomizer { operation, _ -> addTenantIdHeader(operation) }
    }

    private fun addTenantIdHeader(operation: Operation): Operation {
        val alreadyPresent = operation.parameters
            ?.any { it.name == HttpHeader.TENANT_ID && "header" == it.`in` }
            ?: false
        if (alreadyPresent) {
            return operation
        }

        operation.addParametersItem(
            Parameter()
                .`in`("header")
                .name(HttpHeader.TENANT_ID)
                .description("Tenant identifier")
                .required(false)
                .schema(StringSchema())
        )
        return operation
    }
}
