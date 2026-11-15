package com.wutsi.ndopify.visit.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramException
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.server.bot.VisitCancelledBotlet
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.mail.VisitCancelledMailet
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.Test

class VisitCancelledNotifierTest {
    private val botlet = mock<VisitCancelledBotlet>()
    private val mailet = mock<VisitCancelledMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = VisitCancelledNotifier(botlet, mailet, telegramService)

    private val visit = VisitEntity(id = 1L, agentId = 20L, status = VisitStatus.CANCELLED)
    private val customer = CustomerEntity(id = 10L)
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))
    private val listing = ListingEntity(id = 101L)

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        notifier.send(visit, customer, agent, listing)

        verify(mailet).sendToAgent(visit, customer, agent, listing)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a linked agent is messaged, not e-mailed`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)

        notifier.send(visit, customer, agent, listing)

        verify(botlet).send(visit, customer, agent, listing, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `a failed Telegram send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).send(visit, customer, agent, listing, 1000L)

        notifier.send(visit, customer, agent, listing)

        verify(mailet).sendToAgent(visit, customer, agent, listing)
    }
}
