package com.wutsi.ndopify.platform.telegram

import com.wutsi.ndopify.platform.telegram.model.TelegramUpdate

/**
 * Answers a Telegram update: a `callback_query` (the tap on an inline button) goes to the [TelegramCallbackHandler];
 * a `message` gets the reply of the first [TelegramCommand] that recognizes it, or `OK` when none does; every other
 * update type (`edited_message`, ...) is ignored.
 *
 * @param callbackHandler null when no bot message has buttons yet: a `callback_query` is then ignored
 */
class TelegramUpdateHandler(
    private val client: TelegramClient,
    private val commands: List<TelegramCommand>,
    private val callbackHandler: TelegramCallbackHandler? = null,
) {
    fun handle(update: TelegramUpdate) {
        update.callbackQuery?.let { callback ->
            callbackHandler?.handle(callback)
            return
        }

        val message = update.message ?: return
        client.sendMessage(message.chat.id, commands.firstNotNullOfOrNull { it.replyTo(message) } ?: "OK")
    }
}
