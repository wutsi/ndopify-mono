package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.match.server.bot.CoBrokerageInterestBotlet
import com.wutsi.ndopify.match.server.mail.CoBrokerageInterestMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Tells a listing agent about the other agents' interest in its listings: over Telegram when #135's link gives it a
 * chat ([CoBrokerageInterestBotlet]), by e-mail otherwise ([CoBrokerageInterestMailet]).
 *
 * The e-mail is also the fallback of a failed Telegram send (a blocked bot, a deleted chat, Telegram down), so that an
 * interest is never lost. The failure is logged without the bot token nor the Telegram URL. When the e-mail fails too,
 * the notifier lets its exception through: the caller then leaves the matches unnotified, for the next run.
 */
@Service
class CoBrokerageInterestNotifier(
    private val botlet: CoBrokerageInterestBotlet,
    private val mailet: CoBrokerageInterestMailet,
    private val telegramService: AgentTelegramService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CoBrokerageInterestNotifier::class.java)
    }

    fun send(agent: AgentEntity, digest: CoBrokerageInterestDigest) {
        val chatId = telegramService.chatId(agent.id!!)
        if (chatId == null) {
            mailet.send(agent, digest)
            return
        }

        try {
            botlet.send(agent, digest, chatId)
        } catch (ex: Exception) {
            // Any transport failure falls back, not only TelegramException
            LOGGER.warn("Telegram co-brokerage interest failed for Agent#${agent.id}, falling back to e-mail", ex)
            mailet.send(agent, digest)
        }
    }
}
