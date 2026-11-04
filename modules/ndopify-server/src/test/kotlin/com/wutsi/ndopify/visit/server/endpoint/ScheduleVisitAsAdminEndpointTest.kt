package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.refdata.dto.RoleCode
import com.wutsi.ndopify.visit.dto.ScheduleVisitRequest
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

/** An admin may schedule a visit for an agent. */
@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/ScheduleVisitEndpoint.sql"])
class ScheduleVisitAsAdminEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var dao: VisitRepository

    @Test
    fun schedule() {
        val zone = ZoneId.of("Africa/Douala")
        val at = Date.from(
            LocalDate.now(zone).plusDays(2).atTime(10, 0).atZone(zone).toInstant(),
        )

        val response = rest.postForEntity("/v1/visits/1/schedule", ScheduleVisitRequest(scheduledAt = at), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(VisitStatus.CONFIRMED, dao.findById(1L).get().status)
    }
}
