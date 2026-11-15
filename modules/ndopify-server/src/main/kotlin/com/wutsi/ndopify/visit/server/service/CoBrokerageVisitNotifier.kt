package com.wutsi.ndopify.visit.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.visit.server.bot.CoBrokerageVisitBotlet
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.mail.CoBrokerageVisitMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Tells the listing agent (B) about a co-brokerage visit: over Telegram when #135's link gives it a chat
 * ([CoBrokerageVisitBotlet]), by e-mail otherwise ([CoBrokerageVisitMailet]).
 *
 * The e-mail is also the fallback of a failed Telegram send (a blocked bot, a deleted chat, Telegram down), so that B
 * is never left uninformed. The failure is logged without the bot token nor the Telegram URL.
 */
@Service
class CoBrokerageVisitNotifier(
    private val botlet: CoBrokerageVisitBotlet,
    private val mailet: CoBrokerageVisitMailet,
    private val telegramService: AgentTelegramService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CoBrokerageVisitNotifier::class.java)
    }

    fun sendConfirmed(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity) = send(
        visit,
        listing,
        agent,
        bot = { chatId -> botlet.sendConfirmed(visit, listing, agent, chatId) },
        mail = { mailet.sendConfirmed(visit, listing, agent) },
    )

    fun sendReminder(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity) = send(
        visit,
        listing,
        agent,
        bot = { chatId -> botlet.sendReminder(visit, listing, agent, chatId) },
        mail = { mailet.sendReminder(visit, listing, agent) },
    )

    fun sendCancelled(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity) = send(
        visit,
        listing,
        agent,
        bot = { chatId -> botlet.sendCancelled(visit, listing, agent, chatId) },
        mail = { mailet.sendCancelled(visit, listing, agent) },
    )

    fun sendNoShow(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity) = send(
        visit,
        listing,
        agent,
        bot = { chatId -> botlet.sendNoShow(visit, listing, agent, chatId) },
        mail = { mailet.sendNoShow(visit, listing, agent) },
    )

    private fun send(
        visit: VisitEntity,
        listing: ListingEntity,
        agent: AgentEntity,
        bot: (Long) -> Unit,
        mail: () -> Unit,
    ) {
        val chatId = telegramService.chatId(listing.agent.id!!)
        if (chatId == null) {
            mail()
            return
        }

        try {
            bot(chatId)
        } catch (ex: Exception) {
            // Any transport failure falls back, not only TelegramException: B is never left uninformed
            LOGGER.warn("Telegram co-brokerage visit failed for Visit#${visit.id}, falling back to e-mail", ex)
            mail()
        }
    }
}
