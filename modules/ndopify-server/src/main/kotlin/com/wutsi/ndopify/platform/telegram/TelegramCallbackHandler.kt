package com.wutsi.ndopify.platform.telegram

import com.wutsi.ndopify.platform.telegram.model.TelegramCallbackQuery

/**
 * Answers the tap on a message's inline button. The bot's update handler routes every `callback_query` here, and
 * nothing else: a plain message keeps the commands' replies.
 */
interface TelegramCallbackHandler {
    /**
     * Handles one tap. It must contain its own failures: the webhook answers 200 whatever happens, and no exception may
     * escape it.
     */
    fun handle(callback: TelegramCallbackQuery)
}
