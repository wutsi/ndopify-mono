package com.wutsi.ndopify.platform.tenant.jpa

import com.wutsi.ndopify.platform.tenant.TenantContext
import jakarta.persistence.EntityManagerFactory
import org.springframework.orm.jpa.EntityManagerHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Opens a new request session in the tenant [TenantContext] names, when the tenant is only known once the request
 * started.
 *
 * Spring's open-entity-manager-in-view creates the request's session at the start of the request, in the tenant
 * `TenantContextFilter` resolved. A global endpoint has none: a Telegram update carries no token and no `X-Tenant-ID`,
 * and the chat's global link (`T_AGENT_TELEGRAM`) names the tenant only after a first, tenant-less read. Closing that
 * session and binding a new one makes the tenant-scoped services called afterwards (`@Transactional` or a repository
 * call) run in the link's tenant, instead of failing closed to [TenantIdentifierResolver.NO_TENANT].
 *
 * Call [reopen] outside a transaction, right after [TenantContext.set]: removing the tenant stays the caller's job.
 */
@Component
class TenantSessionScope(
    private val entityManagerFactory: EntityManagerFactory,
) {
    fun reopen() {
        val holder = TransactionSynchronizationManager.unbindResourceIfPossible(entityManagerFactory)
            as? EntityManagerHolder ?: return // No request session to replace (a job, a test thread)
        if (holder.entityManager.isOpen) {
            holder.entityManager.close()
        }
        TransactionSynchronizationManager.bindResource(
            entityManagerFactory,
            EntityManagerHolder(entityManagerFactory.createEntityManager()),
        )
    }
}
