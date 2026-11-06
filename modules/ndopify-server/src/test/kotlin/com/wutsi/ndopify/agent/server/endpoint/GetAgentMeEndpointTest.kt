package com.wutsi.ndopify.agent.server.endpoint

import com.wutsi.ndopify.agent.dto.GetAgentResponse
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(
    scripts = ["/db/test/clean.sql", "/db/test/agent/GetAgentEndpoint.sql"],
    statements = [
        "INSERT INTO T_USER(id, email) VALUES (11, 'ray.sponsible@gmail.com'), (12, 'no.agent@example.com'), (13, 'no.party@example.com')",
        "INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES (101, 1, 'no.agent@example.com', 'No', 'Agent')",
        "INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id) VALUES (11, 1, 11, 100), (12, 1, 12, 101), (13, 1, 13, NULL)",
    ],
)
class GetAgentMeEndpointTest : AbstractAgentEndpointTest() {
    @Test
    fun me() {
        val response = rest.getForEntity("/v1/agents/me", GetAgentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val agent = response.body!!.agent
        assertEquals(1L, agent.id)
        assertEquals(100L, agent.party.id)
        assertEquals("Top agent in town", agent.biography)
    }

    @Test
    fun `not an agent`() {
        userId = 12L

        val response = rest.getForEntity("/v1/agents/me", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `caller without party`() {
        userId = 13L

        val response = rest.getForEntity("/v1/agents/me", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/agents/me", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }
}
