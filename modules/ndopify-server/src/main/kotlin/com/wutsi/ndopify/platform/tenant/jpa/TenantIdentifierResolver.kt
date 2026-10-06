package com.wutsi.ndopify.platform.tenant.jpa

import com.wutsi.ndopify.platform.tenant.TenantContext
import org.hibernate.context.spi.CurrentTenantIdentifierResolver

/**
 * Resolves the Hibernate `@TenantId` discriminator from [TenantContext]. Falls back to [NO_TENANT] when the
 * current request has no resolved tenant, so tenant-scoped entities fail closed (match nothing) rather than
 * leaking across tenants.
 */
class TenantIdentifierResolver : CurrentTenantIdentifierResolver<Long> {
    companion object {
        const val NO_TENANT = -1L
    }

    override fun resolveCurrentTenantIdentifier(): Long {
        return TenantContext.get() ?: NO_TENANT
    }

    override fun validateExistingCurrentSessions(): Boolean = false
}
