package com.wutsi.ndopify.visit.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.visit.server.bot.VisitCancelledBotlet
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.mail.VisitCancelledMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Tells an agent that its customer cancelled a scheduled visit: over Telegram when #135's link gives it a chat, by
 * e-mail otherwise.
 *
 * The e-mail is also the fallback of a failed Telegram send (a blocked bot, a deleted chat, Telegram down), so that an
 * agent never keeps a slot that is already gone. The failure is logged without the bot token nor the Telegram URL.
 *
 * Only a customer-initiated cancellation reaches it, for the agent-addressed copy: the caller (`VisitService`) routes.
 */
@Service
class VisitCancelledNotifier(
    private val botlet: VisitCancelledBotlet,
    private val mailet: VisitCancelledMailet,
    private val telegramService: AgentTelegramService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(VisitCancelledNotifier::class.java)
    }

    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity) {
        val chatId = telegramService.chatId(agent.id!!)
        if (chatId == null) {
            mailet.sendToAgent(visit, customer, agent, listing)
            return
        }

        try {
            botlet.send(visit, customer, agent, listing, chatId)
        } catch (ex: Exception) {
            // Any transport failure falls back, not only TelegramException: the agent is never left waiting on a slot
            LOGGER.warn("Telegram visit cancellation failed for Visit#${visit.id}, falling back to e-mail", ex)
            mailet.sendToAgent(visit, customer, agent, listing)
        }
    }
}
