package com.wutsi.ndopify.platform.telegram

import com.wutsi.ndopify.platform.telegram.model.TelegramBooleanResponse
import com.wutsi.ndopify.platform.telegram.model.TelegramMessageResponse
import com.wutsi.ndopify.platform.telegram.model.TelegramResponse
import com.wutsi.ndopify.platform.telegram.model.TelegramUpdate
import com.wutsi.ndopify.platform.telegram.model.TelegramUpdatesResponse
import com.wutsi.ndopify.platform.telegram.model.TelegramUser
import com.wutsi.ndopify.platform.telegram.model.TelegramUserResponse
import com.wutsi.ndopify.platform.util.http.Http

/**
 * The five Bot API calls ndopify-server makes, over [Http].
 *
 * The bot token is part of every URL: it is never logged, and no exception carries the URL (only Telegram's
 * `description`).
 */
open class TelegramClient(
    private val token: String,
    private val http: Http,
) {
    companion object {
        private const val BASE_URL = "https://api.telegram.org"
    }

    open fun sendMessage(chatId: Long, text: String) {
        http.post(
            url("sendMessage"),
            mapOf("chat_id" to chatId, "text" to text),
            TelegramMessageResponse::class.java,
            emptyMap(),
        ).check()
    }

    open fun getMe(): TelegramUser {
        val response = http.get(
            url("getMe"),
            TelegramUserResponse::class.java,
            emptyMap(),
        ).check()
        return response.result ?: throw TelegramException("Empty response from Telegram")
    }

    open fun getUpdates(offset: Long?, timeoutSeconds: Int): List<TelegramUpdate> {
        val response = http.get(
            url("getUpdates?offset=${offset ?: ""}&timeout=$timeoutSeconds"),
            TelegramUpdatesResponse::class.java,
            emptyMap(),
        ).check()
        return response.result ?: emptyList()
    }

    open fun setWebhook(url: String, secret: String) {
        http.post(
            url("setWebhook"),
            mapOf("url" to url, "secret_token" to secret),
            TelegramBooleanResponse::class.java,
            emptyMap(),
        ).check()
    }

    open fun deleteWebhook() {
        http.post(
            url("deleteWebhook"),
            emptyMap<String, Any>(),
            TelegramBooleanResponse::class.java,
            emptyMap(),
        ).check()
    }

    private fun url(path: String) = "$BASE_URL/bot$token/$path"

    private fun <T : TelegramResponse> T?.check(): T {
        if (this == null) {
            throw TelegramException("Empty response from Telegram")
        }
        if (!ok) {
            throw TelegramException(description ?: "Telegram API error")
        }
        return this
    }
}
