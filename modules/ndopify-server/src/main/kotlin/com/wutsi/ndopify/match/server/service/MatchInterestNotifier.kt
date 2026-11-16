package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.server.bot.MatchInterestBotlet
import com.wutsi.ndopify.match.server.mail.MatchInterestMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Tells an agent that its customer asked for a listing on its public page, so that its search now has it: over Telegram
 * when #135's link gives it a chat, by e-mail otherwise.
 *
 * The e-mail is also the fallback of a failed Telegram send (a blocked bot, a deleted chat, Telegram down): the match is
 * already stored, so a missed message only delays the agent. The failure is logged without the bot token nor the
 * Telegram URL.
 */
@Service
class MatchInterestNotifier(
    private val botlet: MatchInterestBotlet,
    private val mailet: MatchInterestMailet,
    private val telegramService: AgentTelegramService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchInterestNotifier::class.java)
    }

    fun send(agent: AgentEntity, customer: CustomerEntity, mandate: MandateEntity, listing: ListingEntity) {
        val chatId = telegramService.chatId(agent.id!!)
        if (chatId == null) {
            mailet.send(agent, customer, mandate, listing)
            return
        }

        try {
            botlet.send(agent, customer, mandate, listing, chatId)
        } catch (ex: Exception) {
            // Any transport failure falls back, not only TelegramException: the agent must learn it
            LOGGER.warn("Telegram match interest failed for Mandate#${mandate.id}, falling back to e-mail", ex)
            mailet.send(agent, customer, mandate, listing)
        }
    }
}
