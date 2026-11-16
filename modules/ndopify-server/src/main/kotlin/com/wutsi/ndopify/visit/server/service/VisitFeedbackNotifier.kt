package com.wutsi.ndopify.visit.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.visit.server.bot.VisitFeedbackBotlet
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.mail.VisitFeedbackMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Tells an agent that its customer answered the visit feedback form: over Telegram when #135's link gives it a chat, by
 * e-mail otherwise.
 *
 * The e-mail is also the fallback of a failed Telegram send (a blocked bot, a deleted chat, Telegram down), so that an
 * answer is never lost. The failure is logged without the bot token nor the Telegram URL.
 */
@Service
class VisitFeedbackNotifier(
    private val botlet: VisitFeedbackBotlet,
    private val mailet: VisitFeedbackMailet,
    private val telegramService: AgentTelegramService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(VisitFeedbackNotifier::class.java)
    }

    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity) {
        val chatId = telegramService.chatId(agent.id!!)
        if (chatId == null) {
            mailet.send(visit, customer, agent, listing)
            return
        }

        try {
            botlet.send(visit, customer, agent, listing, chatId)
        } catch (ex: Exception) {
            // Any transport failure falls back, not only TelegramException: an answer is never lost
            LOGGER.warn("Telegram visit feedback failed for Visit#${visit.id}, falling back to e-mail", ex)
            mailet.send(visit, customer, agent, listing)
        }
    }
}
