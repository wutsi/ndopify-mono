package com.wutsi.ndopify.config

import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.security.server.service.AccessTokenService
import com.wutsi.ndopify.security.server.servlet.TenantContextFilter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.HandlerExceptionResolver

@Configuration
open class TenantConfiguration(
    private val accessTokenService: AccessTokenService,
    private val tenantService: TenantService,
) {
    @Bean
    open fun tenantContextFilter(
        @Qualifier("handlerExceptionResolver") exceptionResolver: HandlerExceptionResolver,
    ): FilterRegistrationBean<TenantContextFilter> {
        val filter = FilterRegistrationBean(TenantContextFilter(accessTokenService, tenantService, exceptionResolver))
        filter.order = Ordered.HIGHEST_PRECEDENCE
        return filter
    }
}
