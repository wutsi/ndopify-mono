package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.server.bot.MandateOfferBotlet
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.mail.MandateOfferMailet
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramException
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.Test

class AssignmentOfferNotifierTest {
    private val botlet = mock<MandateOfferBotlet>()
    private val mailet = mock<MandateOfferMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = AssignmentOfferNotifier(botlet, mailet, telegramService)

    private val assignment = AssignmentEntity(id = 99L, agentId = 20L, status = AssignmentStatus.PENDING)
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        notifier.send(assignment, agent)

        verify(mailet).send(assignment, agent)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a linked agent is messaged, not e-mailed`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)

        notifier.send(assignment, agent)

        verify(botlet).send(assignment, agent, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `a failed Telegram send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).send(assignment, agent, 1000L)

        notifier.send(assignment, agent)

        verify(mailet).send(assignment, agent)
    }
}
