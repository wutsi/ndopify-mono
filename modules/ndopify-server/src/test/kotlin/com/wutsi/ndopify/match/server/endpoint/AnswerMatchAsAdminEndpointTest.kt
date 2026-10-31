package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.RejectMatchRequest
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** Only the customer answers: no admin bypass. */
@Sql(value = ["/db/test/clean.sql", "/db/test/match/ProposeMatchEndpoint.sql"])
class AnswerMatchAsAdminEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var dao: MatchRepository

    @Test
    fun accept() {
        val response = rest.postForEntity("/v1/matches/2/accept", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertEquals(MatchStatus.PROPOSED, dao.findById(2L).get().status)
    }

    @Test
    fun reject() {
        val response = rest.postForEntity("/v1/matches/2/reject", RejectMatchRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertEquals(MatchStatus.PROPOSED, dao.findById(2L).get().status)
    }
}
