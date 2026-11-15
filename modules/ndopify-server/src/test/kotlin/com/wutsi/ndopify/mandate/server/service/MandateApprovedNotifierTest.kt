package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.mandate.server.bot.MandateApprovedBotlet
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.mail.MandateApprovedMailet
import com.wutsi.ndopify.platform.telegram.TelegramException
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.Test

class MandateApprovedNotifierTest {
    private val botlet = mock<MandateApprovedBotlet>()
    private val mailet = mock<MandateApprovedMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = MandateApprovedNotifier(botlet, mailet, telegramService)

    private val mandate = MandateEntity(id = 1L, agentId = 20L)
    private val contract = ContractEntity(id = "c-1")
    private val customer = CustomerEntity(id = 10L)
    private val agent = AgentEntity(id = 20L)

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        notifier.send(mandate, contract, customer, agent)

        verify(mailet).send(mandate, contract, customer, agent)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a linked agent is messaged, not e-mailed`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)

        notifier.send(mandate, contract, customer, agent)

        verify(botlet).send(mandate, agent, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `a failed Telegram send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).send(mandate, agent, 1000L)

        notifier.send(mandate, contract, customer, agent)

        verify(mailet).send(mandate, contract, customer, agent)
    }
}
