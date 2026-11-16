package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.match.server.bot.MatchAnsweredBotlet
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.mail.MatchAnsweredMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Tells the search agent that its customer answered a proposed match: over Telegram when #135's link gives it a chat
 * ([MatchAnsweredBotlet]), by e-mail otherwise ([MatchAnsweredMailet]).
 *
 * The e-mail is also the fallback of a failed Telegram send (a blocked bot, a deleted chat, Telegram down), so that the
 * agent always hears the answer. The failure is logged without the bot token nor the Telegram URL.
 */
@Service
class MatchAnsweredNotifier(
    private val botlet: MatchAnsweredBotlet,
    private val mailet: MatchAnsweredMailet,
    private val telegramService: AgentTelegramService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchAnsweredNotifier::class.java)
    }

    fun send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity) {
        val chatId = telegramService.chatId(agent.id!!)
        if (chatId == null) {
            mailet.send(match, customer, agent)
            return
        }

        try {
            botlet.send(match, customer, agent, chatId)
        } catch (ex: Exception) {
            // Any transport failure falls back, not only TelegramException: the agent is never left uninformed
            LOGGER.warn("Telegram match answered notification failed for Match#${match.id}, falling back to e-mail", ex)
            mailet.send(match, customer, agent)
        }
    }
}
