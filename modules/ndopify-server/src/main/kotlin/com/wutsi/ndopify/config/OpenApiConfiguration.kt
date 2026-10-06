package com.wutsi.ndopify.config

import com.wutsi.ndopify.common.dto.HttpHeader
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import org.springdoc.core.customizers.GlobalOperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class OpenApiConfiguration {
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
