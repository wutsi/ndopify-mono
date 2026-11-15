package com.wutsi.ndopify.visit.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramException
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.server.bot.CoBrokerageVisitBotlet
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.mail.CoBrokerageVisitMailet
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.Test

class CoBrokerageVisitNotifierTest {
    private val botlet = mock<CoBrokerageVisitBotlet>()
    private val mailet = mock<CoBrokerageVisitMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = CoBrokerageVisitNotifier(botlet, mailet, telegramService)

    private val listingAgent = AgentEntity(id = 30L, party = PartyEntity(firstName = "John", lastName = "Doe"))
    private val searchAgent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))
    private val listing = ListingEntity(id = 101L, agent = listingAgent)
    private val visit = VisitEntity(id = 1L, tenantId = 1L, listingId = 101L, agentId = 20L, status = VisitStatus.CONFIRMED)

    @Test
    fun `an unlinked listing agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(30L)

        notifier.sendConfirmed(visit, listing, searchAgent)

        verify(mailet).sendConfirmed(visit, listing, searchAgent)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a linked listing agent is messaged, not e-mailed`() {
        doReturn(1000L).whenever(telegramService).chatId(30L)

        notifier.sendConfirmed(visit, listing, searchAgent)

        verify(botlet).sendConfirmed(visit, listing, searchAgent, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `a failed Telegram send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(30L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).sendConfirmed(visit, listing, searchAgent, 1000L)

        notifier.sendConfirmed(visit, listing, searchAgent)

        verify(mailet).sendConfirmed(visit, listing, searchAgent)
    }

    @Test
    fun `each trigger reaches its own botlet method`() {
        doReturn(1000L).whenever(telegramService).chatId(30L)

        notifier.sendConfirmed(visit, listing, searchAgent)
        notifier.sendReminder(visit, listing, searchAgent)
        notifier.sendCancelled(visit, listing, searchAgent)
        notifier.sendNoShow(visit, listing, searchAgent)

        verify(botlet).sendConfirmed(visit, listing, searchAgent, 1000L)
        verify(botlet).sendReminder(visit, listing, searchAgent, 1000L)
        verify(botlet).sendCancelled(visit, listing, searchAgent, 1000L)
        verify(botlet).sendNoShow(visit, listing, searchAgent, 1000L)
    }
}
