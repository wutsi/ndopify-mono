package com.wutsi.ndopify.npc.ui.telegram

/** Rules and wording of the Telegram connection page, kept free of HTTP. */
object TelegramRules {
    /** Flash attribute of the "code sent" confirmation. */
    const val SENT = "telegramCodeSent"

    /** Flash attribute of a failed request, worded by the controller. */
    const val ERROR = "telegramError"

    const val ALREADY_CONNECTED = "Votre compte Telegram est déjà connecté."
    const val SEND_FAILED = "L'envoi du code a échoué. Réessayez dans quelques instants."

    fun botUrl(username: String) = "https://t.me/$username"
}
