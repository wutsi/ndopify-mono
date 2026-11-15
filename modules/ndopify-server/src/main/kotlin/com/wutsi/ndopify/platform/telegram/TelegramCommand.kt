package com.wutsi.ndopify.platform.telegram

import com.wutsi.ndopify.platform.telegram.model.TelegramMessage

/**
 * A bot command: the reply to send to a message, or null when the message isn't its command, so that the next
 * command gets it (and `TelegramUpdateHandler` answers `OK` when none does).
 */
interface TelegramCommand {
    fun replyTo(message: TelegramMessage): String?
}
