package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.match.dto.SearchMatchResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** With `ndopify.endpoints.ops=false` (as in prod), `POST /v1/matches/run` doesn't exist. */
@Sql(value = ["/db/test/clean.sql", "/db/test/match/RunMatchEndpoint.sql"])
@TestPropertySource(properties = ["ndopify.endpoints.ops=false"])
class RunMatchOpsEndpointDisabledTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Test
    fun `not served`() {
        val response = rest.postForEntity("/v1/matches/run?mandate-id=1", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.HTTP_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `the other endpoints are served`() {
        val response = rest.getForEntity("/v1/matches?mandate-id=1", SearchMatchResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.matches.isEmpty())
    }
}
