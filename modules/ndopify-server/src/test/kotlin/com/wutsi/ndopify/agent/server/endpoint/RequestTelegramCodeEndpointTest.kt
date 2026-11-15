package com.wutsi.ndopify.agent.server.endpoint

import com.wutsi.ndopify.agent.server.dao.AgentTelegramRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(
    scripts = ["/db/test/clean.sql", "/db/test/agent/GetAgentEndpoint.sql"],
    statements = [
        "INSERT INTO T_USER(id, email) VALUES (11, 'ray.sponsible@gmail.com'), (12, 'no.agent@example.com')",
        "INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES (101, 1, 'no.agent@example.com', 'No', 'Agent')",
        "INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id) VALUES (11, 1, 11, 100), (12, 1, 12, 101)",
    ],
)
class RequestTelegramCodeEndpointTest : AbstractAgentEndpointTest() {
    @Autowired
    private lateinit var dao: AgentTelegramRepository

    @Test
    fun `request a code`() {
        val response = rest.postForEntity("/v1/agents/me/telegram/code", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val link = dao.findByAgentId(1L)!!
        assertEquals(1L, link.tenantId)
        assertEquals(6, link.code!!.length)
        assertTrue(link.code!!.all(Char::isDigit))
        assertTrue(link.codeExpiresAt!!.after(Date()))
        assertNull(link.chatId)
        awaitAsyncTasks()
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
    }

    @Test
    @Sql(
        statements = [
            "INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES (1, 1, 555, NOW())",
        ],
    )
    fun `an already connected agent cannot request a code`() {
        val response = rest.postForEntity("/v1/agents/me/telegram/code", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AGENT_TELEGRAM_ALREADY_CONNECTED, response.body!!.error.code)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
    }

    @Test
    fun `not an agent`() {
        userId = 12L

        val response = rest.postForEntity("/v1/agents/me/telegram/code", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body!!.error.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/agents/me/telegram/code", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
