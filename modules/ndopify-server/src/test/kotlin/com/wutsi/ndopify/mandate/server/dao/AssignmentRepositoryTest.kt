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

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/AcceptAssignmentEndpoint.sql"])
class AssignmentRepositoryTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: AssignmentRepository

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
}
