package com.wutsi.ndopify.config.platform

import com.wutsi.ndopify.platform.endpoint.OpsEndpointHandlerMapping
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

@Configuration
open class EndpointConfiguration(
    // Off when missing: an environment without the setting never exposes them
    @param:Value("\${ndopify.endpoints.ops:false}") private val opsEnabled: Boolean,
) {
    @Bean
    open fun webMvcRegistrations(): WebMvcRegistrations {
        return object : WebMvcRegistrations {
            override fun getRequestMappingHandlerMapping(): RequestMappingHandlerMapping =
                OpsEndpointHandlerMapping(opsEnabled)
        }
    }
}
