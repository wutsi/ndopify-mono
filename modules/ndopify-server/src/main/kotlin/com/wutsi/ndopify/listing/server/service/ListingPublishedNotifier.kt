package com.wutsi.ndopify.listing.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.bot.ListingPublishedBotlet
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.mail.ListingPublishedMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Tells an agent about the publication of its listing: over Telegram when #135's link gives it a chat, by e-mail
 * otherwise.
 *
 * The e-mail is also the fallback of a failed Telegram text send (a blocked bot, a deleted chat, Telegram down), so
 * that a publication is never lost. A failed photo is already handled inside the botlet, which still sends the text.
 * The failure is logged without the bot token nor the Telegram URL.
 */
@Service
class ListingPublishedNotifier(
    private val botlet: ListingPublishedBotlet,
    private val mailet: ListingPublishedMailet,
    private val telegramService: AgentTelegramService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingPublishedNotifier::class.java)
    }

    fun send(listing: ListingEntity, customer: CustomerEntity, agent: AgentEntity) {
        val chatId = telegramService.chatId(agent.id!!)
        if (chatId == null) {
            mailet.send(listing, customer, agent)
            return
        }

        try {
            botlet.send(listing, customer, agent, chatId)
        } catch (ex: Exception) {
            // Any transport failure falls back, not only TelegramException: a publication is never lost
            LOGGER.warn("Telegram publication failed for Listing#${listing.id}, falling back to e-mail", ex)
            mailet.send(listing, customer, agent)
        }
    }
}
