package com.wutsi.ndopify.platform.telegram

import com.wutsi.ndopify.platform.telegram.model.TelegramUpdate

/**
 * Answers a Telegram update: a `message` gets the reply of the first [TelegramCommand] that recognizes it, or `OK`
 * when none does; every other update type (`edited_message`, `callback_query`, ...) is ignored.
 */
class TelegramUpdateHandler(
    private val client: TelegramClient,
    private val commands: List<TelegramCommand>,
) {
    fun handle(update: TelegramUpdate) {
        val message = update.message ?: return
        client.sendMessage(message.chat.id, commands.firstNotNullOfOrNull { it.replyTo(message) } ?: "OK")
    }
}
