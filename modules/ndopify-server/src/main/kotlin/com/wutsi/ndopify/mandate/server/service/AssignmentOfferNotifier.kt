package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.mandate.server.bot.MandateOfferBotlet
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.mail.MandateOfferMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Tells an agent about the mandate it's offered: over Telegram when #135's link gives it a chat, by e-mail otherwise.
 *
 * The e-mail is also the fallback of a failed Telegram send (a blocked bot, a deleted chat, Telegram down), so that
 * an offer is never lost. The failure is logged without the bot token nor the Telegram URL. No retry, no delivery
 * tracking: the offer's own deadline and re-dispatch are the only safety net.
 */
@Service
class AssignmentOfferNotifier(
    private val botlet: MandateOfferBotlet,
    private val mailet: MandateOfferMailet,
    private val telegramService: AgentTelegramService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AssignmentOfferNotifier::class.java)
    }

    fun send(assignment: AssignmentEntity, agent: AgentEntity) {
        val chatId = telegramService.chatId(agent.id!!)
        if (chatId == null) {
            mailet.send(assignment, agent)
            return
        }

        try {
            botlet.send(assignment, agent, chatId)
        } catch (ex: Exception) {
            // Any transport failure falls back, not only TelegramException: an offer is never lost
            LOGGER.warn("Telegram offer failed for Assignment#${assignment.id}, falling back to e-mail", ex)
            mailet.send(assignment, agent)
        }
    }
}
