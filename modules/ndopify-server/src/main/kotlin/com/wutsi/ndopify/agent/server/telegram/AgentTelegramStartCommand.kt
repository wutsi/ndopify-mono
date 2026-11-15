package com.wutsi.ndopify.agent.server.telegram

import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.platform.telegram.TelegramCommand
import com.wutsi.ndopify.platform.telegram.model.TelegramMessage
import org.springframework.stereotype.Component

/**
 * The bot's `/start` command: welcomes the chat and, unless it is already linked, explains how to connect it.
 *
 * Telegram sends `/start@<bot>` in a group and `/start <payload>` for a deep link, so the command's head is the text
 * up to the first space, matched case-insensitively with or without the bot's name; the payload is ignored. A linked
 * chat is told it is connected instead, so it is never taught how to connect a second time.
 */
@Component
class AgentTelegramStartCommand(
    private val service: AgentTelegramService,
) : TelegramCommand {
    companion object {
        val WELCOME = """
            Bienvenue sur le bot Ndopify.

            Ce bot vous envoie vos notifications Ndopify (mandats, visites, messages).

            Pour le connecter à votre compte :
            1. Dans votre espace agent, ouvrez la page Telegram et demandez un code par e-mail.
            2. Revenez ici et envoyez /connect suivi du code, par exemple /connect 123456.

            Une fois connecté, vos notifications arrivent dans cette conversation.
        """.trimIndent()

        const val ALREADY_CONNECTED =
            "Votre compte Telegram est déjà connecté à Ndopify. Vos notifications arrivent dans cette conversation."

        private const val START = "/start"
        private val WHITESPACE = Regex("\\s+")
    }

    override fun replyTo(message: TelegramMessage): String? {
        val text = message.text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val head = text.split(WHITESPACE, limit = 2)[0].lowercase()
        if (head != START && !head.startsWith("$START@")) {
            return null
        }

        return if (service.isLinked(message.chat.id)) ALREADY_CONNECTED else WELCOME
    }
}
