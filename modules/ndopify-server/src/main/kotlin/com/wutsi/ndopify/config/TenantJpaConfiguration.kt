package com.wutsi.ndopify.config

import com.wutsi.ndopify.platform.tenant.jpa.TenantIdentifierResolver
import org.hibernate.cfg.MultiTenancySettings
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Hibernate's multi-tenancy, kept apart from [TenantConfiguration]: the entityManagerFactory needs these beans, so
 * this class must not depend on anything that needs the entityManagerFactory itself (repositories, services).
 */
@Configuration
open class TenantJpaConfiguration {
    @Bean
    open fun tenantIdentifierResolver(): TenantIdentifierResolver = TenantIdentifierResolver()

    @Bean
    open fun hibernateTenantIdentifierResolverCustomizer(): HibernatePropertiesCustomizer {
        return HibernatePropertiesCustomizer { properties ->
            properties[MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER] = tenantIdentifierResolver()
        }
    }
}
