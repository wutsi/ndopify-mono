package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.match.server.bot.MatchVisitAgentNudgeBotlet
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.mail.MatchVisitAgentNudgeMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Nudges the search agent that its accepted match still has no visit: over Telegram when #135's link gives it a chat
 * ([MatchVisitAgentNudgeBotlet]), by e-mail otherwise ([MatchVisitAgentNudgeMailet]).
 *
 * The e-mail is also the fallback of a failed Telegram send (a blocked bot, a deleted chat, Telegram down), so that the
 * agent always hears it. The failure is logged without the bot token nor the Telegram URL. [send] returns false only
 * when the agent has no channel at all (no chat, no e-mail): there is nothing to retry then. When both channels fail,
 * it throws, and the caller leaves the match unmarked for the next run.
 */
@Service
class MatchVisitAgentNudgeNotifier(
    private val botlet: MatchVisitAgentNudgeBotlet,
    private val mailet: MatchVisitAgentNudgeMailet,
    private val telegramService: AgentTelegramService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchVisitAgentNudgeNotifier::class.java)
    }

    /** True when the agent was reached (Telegram or e-mail), false when it has no channel at all. */
    fun send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity): Boolean {
        val chatId = telegramService.chatId(agent.id!!)
        if (chatId != null) {
            try {
                botlet.send(match, customer, agent, chatId)
                return true
            } catch (ex: Exception) {
                // Any transport failure falls back, not only TelegramException: the agent is never left uninformed
                LOGGER.warn("Telegram visit nudge failed for Match#${match.id}, falling back to e-mail", ex)
            }
        }

        if (agent.party.email.isBlank()) {
            return false
        }
        mailet.send(match, customer, agent)
        return true
    }
}
