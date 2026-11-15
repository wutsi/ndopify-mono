package com.wutsi.ndopify.platform.tenant.jpa

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.platform.tenant.TenantContext
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.orm.jpa.EntityManagerHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import kotlin.test.Test
import kotlin.test.assertEquals

class TenantSessionScopeTest {
    private val emf = mock<EntityManagerFactory>()
    private val scope = TenantSessionScope(emf)

    @Test
    fun `reopens the request's session`() {
        val old = mock<EntityManager>()
        val new = mock<EntityManager>()
        doReturn(true).whenever(old).isOpen
        doReturn(new).whenever(emf).createEntityManager()
        TransactionSynchronizationManager.bindResource(emf, EntityManagerHolder(old))

        try {
            TenantContext.set(1L)
            scope.reopen()

            verify(old).close()
            val bound = TransactionSynchronizationManager.getResource(emf) as EntityManagerHolder
            assertEquals(new, bound.entityManager)
        } finally {
            TransactionSynchronizationManager.unbindResourceIfPossible(emf)
            TenantContext.remove()
        }
    }

    @Test
    fun `no request session, nothing to reopen`() {
        scope.reopen()

        verifyNoInteractions(emf)
    }
}
