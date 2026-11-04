package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import com.wutsi.ndopify.visit.dto.CreateVisitRequest
import com.wutsi.ndopify.visit.dto.VisitType
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

/** The customer alone requests a visit: an admin can't do it for it. */
@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/CreateVisitEndpoint.sql"])
class CreateVisitAsAdminEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Test
    fun create() {
        val response = rest.postForEntity(
            "/v1/visits",
            CreateVisitRequest(
                matchId = 1L,
                type = VisitType.ON_SITE,
                requestedAt = Date.from(
                    LocalDate.now(ZoneId.of("Africa/Douala")).plusDays(1).atTime(10, 0)
                        .atZone(ZoneId.of("Africa/Douala")).toInstant(),
                ),
            ),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }
}
