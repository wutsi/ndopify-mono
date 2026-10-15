package com.wutsi.ndopify.mandate.server.dao

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.platform.tenant.TenantContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.context.jdbc.Sql
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/AcceptAssignmentEndpoint.sql"])
class AssignmentRepositoryTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: AssignmentRepository

    @Autowired
    private lateinit var mandateDao: MandateRepository

    @AfterTest
    fun cleanup() {
        TenantContext.remove()
    }

    @Test
    fun `concurrent transitions - the 2nd write fails`() {
        TenantContext.set(TENANT_ID)
        val read = dao.findById(1).get()

        dao.save(read.copy(status = AssignmentStatus.ACCEPTED))
        assertFailsWith<ObjectOptimisticLockingFailureException> {
            dao.save(read.copy(status = AssignmentStatus.DECLINED))
        }

        assertEquals(AssignmentStatus.ACCEPTED, dao.findById(1).get().status)
    }

    @Test
    fun `exists by mandate and status`() {
        TenantContext.set(TENANT_ID)
        val mandate1 = mandateDao.findById(1).get() // PENDING offer
        val mandate5 = mandateDao.findById(5).get() // DECLINED offer only

        assertTrue(dao.existsByMandateAndStatus(mandate1, AssignmentStatus.PENDING))
        assertFalse(dao.existsByMandateAndStatus(mandate5, AssignmentStatus.PENDING))
        assertTrue(dao.existsByMandateAndStatus(mandate5, AssignmentStatus.DECLINED))
    }

    @Test
    fun `count by agent ids - per agent and status`() {
        TenantContext.set(TENANT_ID)

        val counts = dao.countByAgentIds(listOf(20L, 30L))
            .map { Triple(it.agentId, it.status, it.total) }
            .toSet()

        // Agent 20: offers 1, 4 PENDING, 3 ACCEPTED. Agent 30: offer 2 PENDING, 5 DECLINED
        assertEquals(
            setOf(
                Triple(20L, AssignmentStatus.PENDING, 2L),
                Triple(20L, AssignmentStatus.ACCEPTED, 1L),
                Triple(30L, AssignmentStatus.PENDING, 1L),
                Triple(30L, AssignmentStatus.DECLINED, 1L),
            ),
            counts,
        )
    }

    @Test
    fun `count by agent ids - only the agents asked, never another tenant's`() {
        TenantContext.set(TENANT_ID)

        assertEquals(setOf(30L), dao.countByAgentIds(listOf(30L, 90L)).map { it.agentId }.toSet())
    }
}
