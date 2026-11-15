package com.wutsi.ndopify.platform.telegram

/**
 * A Telegram API call answered `ok: false`. The message is Telegram's `description` only: the bot token is part of
 * every URL, so neither it nor the URL ever goes in an exception.
 */
class TelegramException(message: String) : RuntimeException(message)
