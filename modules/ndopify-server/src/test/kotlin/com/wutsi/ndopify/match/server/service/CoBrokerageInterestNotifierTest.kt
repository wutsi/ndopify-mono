package com.wutsi.ndopify.match.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.match.server.bot.CoBrokerageInterestBotlet
import com.wutsi.ndopify.match.server.mail.CoBrokerageInterestMailet
import com.wutsi.ndopify.platform.telegram.TelegramException
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CoBrokerageInterestNotifierTest {
    private val botlet = mock<CoBrokerageInterestBotlet>()
    private val mailet = mock<CoBrokerageInterestMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = CoBrokerageInterestNotifier(botlet, mailet, telegramService)

    private val agent = AgentEntity(id = 30L)
    private val digest = CoBrokerageInterestDigest(TenantEntity(id = 1L), 1, emptyList())

    @Test
    fun `a linked agent gets the digest on Telegram only`() {
        doReturn(777L).whenever(telegramService).chatId(30L)

        notifier.send(agent, digest)

        verify(botlet).send(agent, digest, 777L)
        verify(mailet, never()).send(any(), any())
    }

    @Test
    fun `an unlinked agent gets the e-mail only`() {
        doReturn(null).whenever(telegramService).chatId(30L)

        notifier.send(agent, digest)

        verify(mailet).send(agent, digest)
        verify(botlet, never()).send(any(), any(), any())
    }

    @Test
    fun `Telegram failing falls back to the e-mail`() {
        doReturn(777L).whenever(telegramService).chatId(30L)
        doThrow(TelegramException("Bot was blocked by the user")).whenever(botlet).send(agent, digest, 777L)

        notifier.send(agent, digest)

        verify(mailet).send(agent, digest)
    }

    @Test
    fun `both channels failing throws so the matches stay unnotified`() {
        doReturn(777L).whenever(telegramService).chatId(30L)
        doThrow(TelegramException("Bot was blocked by the user")).whenever(botlet).send(agent, digest, 777L)
        doThrow(RuntimeException("SMTP down")).whenever(mailet).send(agent, digest)

        assertFailsWith<RuntimeException> { notifier.send(agent, digest) }
    }
}
