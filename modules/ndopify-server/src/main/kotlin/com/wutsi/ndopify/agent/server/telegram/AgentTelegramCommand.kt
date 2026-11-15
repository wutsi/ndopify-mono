package com.wutsi.ndopify.agent.server.telegram

import com.wutsi.ndopify.agent.server.service.AgentTelegramLinkResult
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.platform.telegram.TelegramCommand
import com.wutsi.ndopify.platform.telegram.model.TelegramMessage
import org.springframework.stereotype.Component

/**
 * The bot's `/connect <code>` command: links the chat it comes from to the agent account that requested the code.
 *
 * Telegram sends `/connect@<bot>` in a group, so the command's head is the text up to the first space, matched
 * case-insensitively with or without the bot's name; the code must be exactly 6 digits, checked here so that a
 * malformed one never reaches the database.
 */
@Component
class AgentTelegramCommand(
    private val service: AgentTelegramService,
) : TelegramCommand {
    companion object {
        const val CONNECTED =
            "Votre compte Telegram est connecté. Vous recevrez désormais vos notifications Ndopify ici."
        const val INVALID =
            "Ce code est invalide ou expiré. Demandez un nouveau code depuis la page Telegram de votre espace agent."
        const val CHAT_LINKED =
            "Cette conversation Telegram est déjà liée à un autre compte agent. Contactez le support Ndopify."

        private const val CONNECT = "/connect"
        private val WHITESPACE = Regex("\\s+")
    }

    override fun replyTo(message: TelegramMessage): String? {
        val text = message.text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parts = text.split(WHITESPACE, limit = 2)
        val head = parts[0].lowercase()
        if (head != CONNECT && !head.startsWith("$CONNECT@")) {
            return null
        }

        val code = parts.getOrNull(1)?.trim()
        if (code == null || code.length != 6 || !code.all(Char::isDigit)) {
            return INVALID
        }

        return when (service.connect(code, message.chat.id)) {
            AgentTelegramLinkResult.CONNECTED -> CONNECTED
            AgentTelegramLinkResult.INVALID_CODE -> INVALID
            AgentTelegramLinkResult.CHAT_ALREADY_LINKED -> CHAT_LINKED
        }
    }
}
