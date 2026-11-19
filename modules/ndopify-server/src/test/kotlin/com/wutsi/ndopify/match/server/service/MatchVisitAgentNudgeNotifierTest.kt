package com.wutsi.ndopify.match.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.match.server.bot.MatchVisitAgentNudgeBotlet
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.mail.MatchVisitAgentNudgeMailet
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramException
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchVisitAgentNudgeNotifierTest {
    private val botlet = mock<MatchVisitAgentNudgeBotlet>()
    private val mailet = mock<MatchVisitAgentNudgeMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = MatchVisitAgentNudgeNotifier(botlet, mailet, telegramService)

    private val match = MatchEntity(id = 9001L, agentId = 20L)
    private val customer = CustomerEntity(id = 10L)
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )

    @Test
    fun `a linked chat - the botlet only`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)

        assertTrue(notifier.send(match, customer, agent))

        verify(botlet).send(match, customer, agent, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `no chat - the mailet`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        assertTrue(notifier.send(match, customer, agent))

        verify(mailet).send(match, customer, agent)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a failed Telegram send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).send(match, customer, agent, 1000L)

        assertTrue(notifier.send(match, customer, agent))

        verify(mailet).send(match, customer, agent)
    }

    @Test
    fun `a failed fallback throws, so the match stays unmarked`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).send(match, customer, agent, 1000L)
        doThrow(RuntimeException("SMTP down")).whenever(mailet).send(match, customer, agent)

        assertFailsWith<RuntimeException> { notifier.send(match, customer, agent) }
    }

    @Test
    fun `no channel at all - nothing sent, false`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        assertFalse(notifier.send(match, customer, agent.copy(party = agent.party.copy(email = " "))))

        verifyNoInteractions(botlet, mailet)
    }

    @Test
    fun `a chat reached without an e-mail still counts`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)

        assertEquals(true, notifier.send(match, customer, agent.copy(party = agent.party.copy(email = ""))))

        verify(botlet).send(match, customer, agent.copy(party = agent.party.copy(email = "")), 1000L)
    }
}
