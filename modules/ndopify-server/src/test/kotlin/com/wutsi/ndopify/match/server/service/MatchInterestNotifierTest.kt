package com.wutsi.ndopify.match.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.server.bot.MatchInterestBotlet
import com.wutsi.ndopify.match.server.mail.MatchInterestMailet
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramException
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.Test

class MatchInterestNotifierTest {
    private val botlet = mock<MatchInterestBotlet>()
    private val mailet = mock<MatchInterestMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = MatchInterestNotifier(botlet, mailet, telegramService)

    private val customer = CustomerEntity(id = 10L, party = PartyEntity(firstName = "Ray", lastName = "Sponsible"))
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))
    private val mandate = MandateEntity(id = 1L, tenantId = 1L, agentId = 20L)
    private val listing = ListingEntity(id = 101L)

    @Test
    fun `sends over Telegram when the agent is linked`() {
        doReturn(99L).whenever(telegramService).chatId(20L)

        notifier.send(agent, customer, mandate, listing)

        verify(botlet).send(agent, customer, mandate, listing, 99L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `e-mails an agent without a chat`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        notifier.send(agent, customer, mandate, listing)

        verify(mailet).send(agent, customer, mandate, listing)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `falls back to the e-mail when Telegram fails`() {
        doReturn(99L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).send(agent, customer, mandate, listing, 99L)

        notifier.send(agent, customer, mandate, listing)

        verify(mailet).send(agent, customer, mandate, listing)
    }
}
