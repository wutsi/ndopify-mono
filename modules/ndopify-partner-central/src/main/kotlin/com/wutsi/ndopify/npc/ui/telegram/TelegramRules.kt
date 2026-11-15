package com.wutsi.ndopify.npc.ui.telegram

/** Rules and wording of the Telegram connection page, kept free of HTTP. */
object TelegramRules {
    /** Flash attribute of the "code sent" confirmation, shown once by the alert. */
    const val SENT = "telegramCodeSent"

    /**
     * Session attribute of the code's expiry (epoch millis): the state that outlives the flash, since the code
     * itself is valid that long. A refresh keeps the bot button enabled until it passes.
     */
    const val SENT_UNTIL = "telegramCodeSentUntil"

    /** Flash attribute of a failed request, worded by the controller. */
    const val ERROR = "telegramError"

    const val ALREADY_CONNECTED = "Votre compte Telegram est déjà connecté."
    const val SEND_FAILED = "L'envoi du code a échoué. Réessayez dans quelques instants."

    /** The bot the page links to when `TELEGRAM_BOT_USERNAME` is blank: `https://t.me/` never opens any bot. */
    const val DEFAULT_BOT_USERNAME = "ndopify_bot"

    fun botUrl(username: String) = "https://t.me/" + username.ifBlank { DEFAULT_BOT_USERNAME }
}
