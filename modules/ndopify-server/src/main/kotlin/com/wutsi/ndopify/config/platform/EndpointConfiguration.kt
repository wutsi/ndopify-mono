package com.wutsi.ndopify.config.platform

import com.wutsi.ndopify.platform.endpoint.OpsEndpointAuthorizationInterceptor
import com.wutsi.ndopify.platform.endpoint.OpsEndpointHandlerMapping
import com.wutsi.ndopify.security.server.service.CallerService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

@Configuration
open class EndpointConfiguration(
    // Off when missing: an environment without the setting never exposes them
    @param:Value("\${ndopify.endpoints.ops:false}") private val opsEnabled: Boolean,
    private val callerService: CallerService,
) : WebMvcConfigurer {
    @Bean
    open fun webMvcRegistrations(): WebMvcRegistrations {
        return object : WebMvcRegistrations {
            override fun getRequestMappingHandlerMapping(): RequestMappingHandlerMapping =
                OpsEndpointHandlerMapping(opsEnabled)
        }
    }

    // Applied to the mapping webMvcRegistrations() returns too: Spring Boot sets its interceptors on every
    // RequestMappingHandlerMapping it creates (WebMvcConfigurationSupport.initHandlerMapping).
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(OpsEndpointAuthorizationInterceptor(callerService))
    }
}
