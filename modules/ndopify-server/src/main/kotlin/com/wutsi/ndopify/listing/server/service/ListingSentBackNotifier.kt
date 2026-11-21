package com.wutsi.ndopify.listing.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.listing.server.bot.ListingSentBackBotlet
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.mail.ListingSentBackMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Tells an agent that its listing was sent back to DRAFT for correction: over Telegram when #135's link gives it a
 * chat, by e-mail otherwise.
 *
 * The e-mail is also the fallback of a failed Telegram send (a blocked bot, a deleted chat, Telegram down), so that
 * a send-back is never lost. The failure is logged without the bot token nor the Telegram URL.
 */
@Service
class ListingSentBackNotifier(
    private val botlet: ListingSentBackBotlet,
    private val mailet: ListingSentBackMailet,
    private val telegramService: AgentTelegramService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingSentBackNotifier::class.java)
    }

    fun send(listing: ListingEntity, agent: AgentEntity) {
        val chatId = telegramService.chatId(agent.id!!)
        if (chatId == null) {
            mailet.send(listing, agent)
            return
        }

        try {
            botlet.send(listing, chatId)
        } catch (ex: Exception) {
            // Any transport failure falls back, not only TelegramException: a send-back is never lost
            LOGGER.warn("Telegram send-back failed for Listing#${listing.id}, falling back to e-mail", ex)
            mailet.send(listing, agent)
        }
    }
}
