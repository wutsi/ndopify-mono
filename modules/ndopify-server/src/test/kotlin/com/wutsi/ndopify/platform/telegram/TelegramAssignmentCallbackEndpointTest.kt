package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.any
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.AssignmentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.platform.tenant.TenantContext
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestPropertySource(
    properties = [
        "ndopify.telegram.transport=webhook",
        "ndopify.telegram.webhook.url=https://test.example/telegram/webhook",
        "ndopify.telegram.webhook.secret=test-secret",
    ],
)
@Sql(value = ["/db/test/clean.sql", "/db/test/telegram/TelegramAssignmentCallbackEndpoint.sql"])
class TelegramAssignmentCallbackEndpointTest : TenantAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Autowired
    private lateinit var assignmentDao: AssignmentRepository

    @Autowired
    private lateinit var mandateDao: MandateRepository

    private fun tap(data: String, chatId: Long, messageId: Long = 50L): ResponseEntity<String> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Telegram-Bot-Api-Secret-Token", "test-secret")
        }
        val body = """
            {"update_id":1,"callback_query":{"id":"cb-1","data":"$data",
             "message":{"message_id":$messageId,"chat":{"id":$chatId},"text":"Offre"}}}
        """.trimIndent()
        return rest.postForEntity("/telegram/webhook", HttpEntity(body, headers), String::class.java)
    }

    @Test
    fun `accept answers the offer`() {
        assertEquals(HttpStatus.OK, tap("accept:1", 1000L).statusCode)

        assertEquals(AssignmentStatus.ACCEPTED, assignmentDao.findById(1L).get().status)
        assertEquals(MandateStatus.ASSIGNED, mandateDao.findById(1L).get().status)
        verify(client).answerCallbackQuery("cb-1", "Mandat accepté.")
        verify(client).editMessageText(
            1000L,
            50L,
            "Offre\n\nMandat accepté.",
            listOf(TelegramButton("Voir le mandat", url = "https://npc.example/mandates/1")),
        )
        awaitAsyncTasks() // the customer's assigned e-mail
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
    }

    @Test
    fun `a tap from another tenant's chat answers that tenant's offer`() {
        assertEquals(HttpStatus.OK, tap("decline:9", 2000L).statusCode)

        TenantContext.set(2L)
        try {
            assertEquals(AssignmentStatus.DECLINED, assignmentDao.findById(9L).get().status)
        } finally {
            TenantContext.set(TENANT_ID)
        }
        verify(client).answerCallbackQuery("cb-1", "Mandat refusé.")
        verify(client).editMessageText(2000L, 50L, "Offre\n\nMandat refusé.")
    }

    @Test
    fun `a tap on an expired offer changes nothing`() {
        assertEquals(HttpStatus.OK, tap("accept:4", 1000L).statusCode)

        assertEquals(AssignmentStatus.PENDING, assignmentDao.findById(4L).get().status)
        verify(client).answerCallbackQuery("cb-1", "Ce mandat n'est plus disponible.")
        verify(client).editMessageText(1000L, 50L, "Offre\n\nCe mandat n'est plus disponible.")
    }

    @Test
    fun `a tap from a chat of another agent changes nothing`() {
        assertEquals(HttpStatus.OK, tap("accept:1", 2000L).statusCode)

        assertEquals(AssignmentStatus.PENDING, assignmentDao.findById(1L).get().status)
        verify(client).answerCallbackQuery("cb-1", "Action impossible.")
        verify(client, never()).editMessageText(any(), any(), any(), any())
    }
}
