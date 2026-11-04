package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.refdata.dto.RoleCode
import com.wutsi.ndopify.visit.dto.CancelVisitRequest
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** An admin may cancel as the agent, and the visit records the agent as the canceller. */
@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/CancelVisitEndpoint.sql"])
class CancelVisitAsAdminEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var dao: VisitRepository

    @Test
    fun cancel() {
        val response = rest.postForEntity("/v1/visits/2/cancel", CancelVisitRequest(reason = "Admin"), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = dao.findById(2L).get()
        assertEquals(VisitStatus.CANCELLED, visit.status)
        assertEquals(VisitParty.AGENT, visit.cancelledBy)
    }
}
