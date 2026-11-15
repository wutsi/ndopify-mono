package com.wutsi.ndopify.platform.telegram

import com.wutsi.ndopify.platform.telegram.model.TelegramUpdate

/**
 * Answers a Telegram update: every update carrying a `message` gets the reply `OK`, whatever it says, and every other
 * update type (`edited_message`, `callback_query`, ...) is ignored. There's no command routing yet.
 */
class TelegramUpdateHandler(
    private val client: TelegramClient,
) {
    fun handle(update: TelegramUpdate) {
        val message = update.message ?: return
        client.sendMessage(message.chat.id, "OK")
    }
}
