package com.wutsi.ndopify.match.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.bot.MatchAnsweredBotlet
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.mail.MatchAnsweredMailet
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramException
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.Test

class MatchAnsweredNotifierTest {
    private val botlet = mock<MatchAnsweredBotlet>()
    private val mailet = mock<MatchAnsweredMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = MatchAnsweredNotifier(botlet, mailet, telegramService)

    private val match = MatchEntity(id = 2L, agentId = 20L, status = MatchStatus.ACCEPTED)
    private val customer = CustomerEntity(id = 10L)
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        notifier.send(match, customer, agent)

        verify(mailet).send(match, customer, agent)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a linked agent is messaged, not e-mailed`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)

        notifier.send(match, customer, agent)

        verify(botlet).send(match, customer, agent, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `a failed Telegram send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).send(match, customer, agent, 1000L)

        notifier.send(match, customer, agent)

        verify(mailet).send(match, customer, agent)
    }
}
