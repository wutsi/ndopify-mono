package com.wutsi.ndopify.config

import com.wutsi.ndopify.platform.tenant.jpa.TenantIdentifierResolver
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.security.server.service.AccessTokenService
import com.wutsi.ndopify.security.server.servlet.TenantContextFilter
import org.hibernate.cfg.MultiTenancySettings
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration
open class TenantConfiguration(
    private val accessTokenService: AccessTokenService,
    private val tenantService: TenantService,
) {
    @Bean
    open fun tenantContextFilter(): FilterRegistrationBean<TenantContextFilter> {
        val filter = FilterRegistrationBean(TenantContextFilter(accessTokenService, tenantService))
        filter.order = Ordered.HIGHEST_PRECEDENCE
        return filter
    }

    @Bean
    open fun tenantIdentifierResolver(): TenantIdentifierResolver = TenantIdentifierResolver()

    @Bean
    open fun hibernateTenantIdentifierResolverCustomizer(): HibernatePropertiesCustomizer {
        return HibernatePropertiesCustomizer { properties ->
            properties[MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER] = tenantIdentifierResolver()
        }
    }
}
