package com.wutsi.ndopify.visit.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.visit.server.bot.VisitReminderBotlet
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.mail.VisitReminderMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Reminds an agent of a confirmed visit 24 h ahead: over Telegram when #135's link gives it a chat, by e-mail
 * otherwise.
 *
 * The e-mail is also the fallback of a failed Telegram send (a blocked bot, a deleted chat, Telegram down), so that the
 * agent learns of the visit before it happens. Only the agent's copy goes through this notifier: the customer's e-mail
 * is [com.wutsi.ndopify.visit.server.mail.VisitReminderMailet.sendCustomer]'s job, and a fallback must never send it a
 * second copy. The failure is logged without the bot token nor the Telegram URL.
 */
@Service
class VisitReminderNotifier(
    private val botlet: VisitReminderBotlet,
    private val mailet: VisitReminderMailet,
    private val telegramService: AgentTelegramService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(VisitReminderNotifier::class.java)
    }

    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity) {
        val chatId = telegramService.chatId(agent.id!!)
        if (chatId == null) {
            mailet.sendAgent(visit, customer, agent, listing)
            return
        }

        try {
            botlet.send(visit, customer, agent, listing, chatId)
        } catch (ex: Exception) {
            // Any transport failure falls back, not only TelegramException: the reminder is never lost
            LOGGER.warn("Telegram visit reminder failed for Visit#${visit.id}, falling back to e-mail", ex)
            mailet.sendAgent(visit, customer, agent, listing)
        }
    }
}
