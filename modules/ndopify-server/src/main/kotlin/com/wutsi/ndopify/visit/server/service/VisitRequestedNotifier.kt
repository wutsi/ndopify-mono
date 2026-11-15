package com.wutsi.ndopify.visit.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.visit.server.bot.VisitRequestedBotlet
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.mail.VisitRequestedMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Tells an agent that its customer asked to visit a listing: over Telegram when #135's link gives it a chat, by e-mail
 * otherwise.
 *
 * The e-mail is also the fallback of a failed Telegram send (a blocked bot, a deleted chat, Telegram down), so that a
 * time-sensitive request is never lost: the visit waits on the agent, and a missed request becomes an `EXPIRED` visit.
 * The failure is logged without the bot token nor the Telegram URL.
 */
@Service
class VisitRequestedNotifier(
    private val botlet: VisitRequestedBotlet,
    private val mailet: VisitRequestedMailet,
    private val telegramService: AgentTelegramService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(VisitRequestedNotifier::class.java)
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
            // Any transport failure falls back, not only TelegramException: a request is never lost
            LOGGER.warn("Telegram visit request failed for Visit#${visit.id}, falling back to e-mail", ex)
            mailet.send(visit, customer, agent, listing)
        }
    }
}
