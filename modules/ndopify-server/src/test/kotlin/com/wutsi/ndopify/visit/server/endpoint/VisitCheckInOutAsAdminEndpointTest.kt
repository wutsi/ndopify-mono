package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import com.wutsi.ndopify.visit.dto.CheckInVisitRequest
import com.wutsi.ndopify.visit.dto.CheckOutVisitRequest
import com.wutsi.ndopify.visit.dto.NoShowVisitRequest
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** Check-in, no-show and check-out are the agent's alone: an admin can't do them. */
@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/CheckInVisitEndpoint.sql"])
class VisitCheckInOutAsAdminEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var dao: VisitRepository

    @Test
    fun `check in`() {
        val response = rest.postForEntity(
            "/v1/visits/5/check-in",
            CheckInVisitRequest(code = "3333"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
        assertEquals(VisitStatus.CONFIRMED, dao.findById(5L).get().status)
    }

    @Test
    fun `no show`() {
        val response = rest.postForEntity("/v1/visits/5/no-show", NoShowVisitRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `check out`() {
        val response = rest.postForEntity(
            "/v1/visits/5/check-out",
            CheckOutVisitRequest(comment = "Non"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }
}
