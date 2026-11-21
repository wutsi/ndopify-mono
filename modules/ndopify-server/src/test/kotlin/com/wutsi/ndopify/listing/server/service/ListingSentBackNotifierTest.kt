package com.wutsi.ndopify.listing.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.listing.server.bot.ListingSentBackBotlet
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.mail.ListingSentBackMailet
import com.wutsi.ndopify.platform.telegram.TelegramException
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.Test

class ListingSentBackNotifierTest {
    private val botlet = mock<ListingSentBackBotlet>()
    private val mailet = mock<ListingSentBackMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = ListingSentBackNotifier(botlet, mailet, telegramService)

    private val listing = ListingEntity(id = 101L)
    private val agent = AgentEntity(id = 20L)

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        notifier.send(listing, agent)

        verify(mailet).send(listing, agent)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a linked agent is messaged, not e-mailed`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)

        notifier.send(listing, agent)

        verify(botlet).send(listing, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `a failed Telegram send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user")).whenever(botlet).send(listing, 1000L)

        notifier.send(listing, agent)

        verify(mailet).send(listing, agent)
    }
}
