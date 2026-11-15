package com.wutsi.ndopify.agent.server.endpoint

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.agent.server.dao.AgentTelegramRepository
import com.wutsi.ndopify.agent.server.telegram.AgentTelegramCommand
import com.wutsi.ndopify.platform.telegram.TelegramClient
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestPropertySource(
    properties = [
        "ndopify.telegram.transport=webhook",
        "ndopify.telegram.webhook.url=https://test.example/telegram/webhook",
        "ndopify.telegram.webhook.secret=test-secret",
    ],
)
@Sql(
    scripts = ["/db/test/clean.sql", "/db/test/agent/GetAgentEndpoint.sql"],
    statements = [
        // Agent 2 belongs to tenant 2; the webhook below carries no token and no tenant.
        "INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, code, code_expires_at) " +
            "VALUES (2, 2, '123456', DATE_ADD(UTC_TIMESTAMP(), INTERVAL 10 MINUTE))",
    ],
)
class AgentTelegramWebhookEndpointTest : BaseEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Autowired
    private lateinit var dao: AgentTelegramRepository

    @Test
    fun `a connect from another tenant links its agent`() {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Telegram-Bot-Api-Secret-Token", "test-secret")
        }
        val body = """{"update_id":1,"message":{"message_id":2,"chat":{"id":777},"text":"/connect 123456"}}"""

        val response = rest.postForEntity("/telegram/webhook", HttpEntity(body, headers), Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val link = dao.findByAgentId(2L)!!
        assertEquals(777L, link.chatId)
        assertNull(link.code)
        assertNull(link.codeExpiresAt)
        verify(client).sendMessage(777L, AgentTelegramCommand.CONNECTED)
    }

    @Test
    fun `an unknown code gets the invalid message and writes nothing`() {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Telegram-Bot-Api-Secret-Token", "test-secret")
        }
        val body = """{"update_id":2,"message":{"message_id":3,"chat":{"id":777},"text":"/connect 000000"}}"""

        val response = rest.postForEntity("/telegram/webhook", HttpEntity(body, headers), Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val link = dao.findByAgentId(2L)!!
        assertNull(link.chatId)
        assertEquals("123456", link.code)
        verify(client).sendMessage(777L, AgentTelegramCommand.INVALID)
    }
}
