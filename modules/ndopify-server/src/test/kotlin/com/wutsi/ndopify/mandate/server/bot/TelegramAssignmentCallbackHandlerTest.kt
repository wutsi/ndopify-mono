package com.wutsi.ndopify.mandate.server.bot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentTelegramEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.service.AssignmentService
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.platform.telegram.model.TelegramCallbackQuery
import com.wutsi.ndopify.platform.telegram.model.TelegramChat
import com.wutsi.ndopify.platform.telegram.model.TelegramMessage
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.mockito.Mockito.never
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class TelegramAssignmentCallbackHandlerTest {
    private val client = mock<TelegramClient>()
    private val telegramService = mock<AgentTelegramService>()
    private val service = mock<AssignmentService>()
    private val tenantService = mock<TenantService>()
    private val handler = TelegramAssignmentCallbackHandler(client, telegramService, service, tenantService)

    private val link = AgentTelegramEntity(id = 9L, tenantId = 1L, agentId = 20L, chatId = 1000L)
    private val tenant = TenantEntity(id = 1L, name = "Ndopify", partnerCentralUrl = "https://npc.example")
    private val accepted = AssignmentEntity(
        id = 99L, agentId = 20L, status = AssignmentStatus.ACCEPTED,
        mandate = MandateEntity(id = 10L, tenantId = 1L),
    )
    private val message = TelegramMessage(messageId = 50L, chat = TelegramChat(id = 1000L), text = "Offre")

    private fun callback(data: String?, m: TelegramMessage? = message) =
        TelegramCallbackQuery(id = "cb-1", data = data, message = m)

    @BeforeTest
    fun setUp() {
        doReturn(link).whenever(telegramService).findByChatId(1000L)
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(accepted).whenever(service).acceptForAgent(20L, 99L)
        doReturn(accepted.copy(status = AssignmentStatus.DECLINED)).whenever(service).declineForAgent(20L, 99L)
    }

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `accept answers the offer and edits it with the mandate link`() {
        handler.handle(callback("accept:99"))

        verify(service).acceptForAgent(20L, 99L)
        verify(client).answerCallbackQuery("cb-1", "Mandat accepté.")
        verify(client).editMessageText(
            1000L,
            50L,
            "Offre\n\nMandat accepté.",
            listOf(TelegramButton("Voir le mandat", url = "https://npc.example/mandates/10")),
        )
    }

    @Test
    fun `an accept without a Partner Central URL adds no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        handler.handle(callback("accept:99"))

        verify(client).answerCallbackQuery("cb-1", "Mandat accepté.")
        verify(client).editMessageText(1000L, 50L, "Offre\n\nMandat accepté.")
    }

    @Test
    fun `decline answers the offer and edits the message, without a button`() {
        handler.handle(callback("decline:99"))

        verify(service).declineForAgent(20L, 99L)
        verify(client).answerCallbackQuery("cb-1", "Mandat refusé.")
        verify(client).editMessageText(1000L, 50L, "Offre\n\nMandat refusé.")
    }

    @Test
    fun `an offer that is no longer pending says so and drops the buttons`() {
        doThrow(ConflictException(Error(code = ErrorCode.ASSIGNMENT_NOT_PENDING)))
            .whenever(service).acceptForAgent(20L, 99L)

        handler.handle(callback("accept:99"))

        verify(client).answerCallbackQuery("cb-1", "Ce mandat n'est plus disponible.")
        verify(client).editMessageText(1000L, 50L, "Offre\n\nCe mandat n'est plus disponible.")
    }

    @Test
    fun `another agent's offer changes nothing and reveals nothing`() {
        doThrow(ForbiddenException(Error(code = ErrorCode.ASSIGNMENT_ACCESS_DENIED)))
            .whenever(service).acceptForAgent(20L, 99L)

        handler.handle(callback("accept:99"))

        verify(client).answerCallbackQuery("cb-1", "Action impossible.")
        verify(client, never()).editMessageText(any(), any(), any(), any())
    }

    @Test
    fun `an unknown chat id changes nothing`() {
        doReturn(null).whenever(telegramService).findByChatId(1000L)

        handler.handle(callback("accept:99"))

        verify(client).answerCallbackQuery("cb-1", "Action impossible.")
        verifyNoInteractions(service)
        verify(client, never()).editMessageText(any(), any(), any(), any())
    }

    @Test
    fun `malformed data is only answered`() {
        handler.handle(callback("open:99"))

        verify(client).answerCallbackQuery("cb-1", "Action impossible.")
        verifyNoInteractions(service)
        verify(client, never()).editMessageText(any(), any(), any(), any())
    }

    @Test
    fun `a callback without a message is only answered`() {
        handler.handle(callback("accept:99", m = null))

        verify(client).answerCallbackQuery("cb-1", "Action impossible.")
        verifyNoInteractions(service)
    }

    @Test
    fun `a failing edit does not undo the answer`() {
        doThrow(RuntimeException("message to edit not found"))
            .whenever(client).editMessageText(any(), any(), any(), any())

        handler.handle(callback("accept:99"))

        verify(service).acceptForAgent(20L, 99L)
        verify(client).answerCallbackQuery("cb-1", "Mandat accepté.")
    }

    @Test
    fun `an unexpected failure is contained and the tap still answered`() {
        doThrow(RuntimeException("boom")).whenever(service).acceptForAgent(20L, 99L)

        handler.handle(callback("accept:99"))

        verify(client).answerCallbackQuery("cb-1", "Action impossible.")
    }

    @Test
    fun `a failed toast is contained`() {
        doThrow(RuntimeException("Telegram down")).whenever(client).answerCallbackQuery(any(), any())

        handler.handle(callback("accept:99"))

        verify(service).acceptForAgent(20L, 99L)
    }
}
