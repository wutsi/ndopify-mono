package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.mandate.server.bot.MandateApprovedBotlet
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.mail.MandateApprovedMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Tells an agent that its mandate is approved: over Telegram when #135's link gives it a chat, by e-mail otherwise.
 *
 * The e-mail is also the fallback of a failed Telegram send (a blocked bot, a deleted chat, Telegram down), so that
 * an approval is never lost. The failure is logged without the bot token nor the Telegram URL. No retry, no delivery
 * tracking: the mandate is already approved, whether the agent learns it or not.
 */
@Service
class MandateApprovedNotifier(
    private val botlet: MandateApprovedBotlet,
    private val mailet: MandateApprovedMailet,
    private val telegramService: AgentTelegramService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateApprovedNotifier::class.java)
    }

    fun send(mandate: MandateEntity, contract: ContractEntity, customer: CustomerEntity, agent: AgentEntity) {
        val chatId = telegramService.chatId(agent.id!!)
        if (chatId == null) {
            mailet.send(mandate, contract, customer, agent)
            return
        }

        try {
            botlet.send(mandate, agent, chatId)
        } catch (ex: Exception) {
            // Any transport failure falls back, not only TelegramException: an approval is never lost
            LOGGER.warn("Telegram approval failed for Mandate#${mandate.id}, falling back to e-mail", ex)
            mailet.send(mandate, contract, customer, agent)
        }
    }
}
