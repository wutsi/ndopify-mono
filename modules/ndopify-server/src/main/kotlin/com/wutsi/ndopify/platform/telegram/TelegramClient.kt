package com.wutsi.ndopify.platform.telegram

import com.wutsi.ndopify.platform.telegram.model.TelegramBooleanResponse
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
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

    open fun sendMessage(chatId: Long, text: String, buttons: List<TelegramButton> = emptyList()) {
        val payload = buildMap<String, Any> {
            put("chat_id", chatId)
            put("text", text)
            if (buttons.isNotEmpty()) {
                put("reply_markup", mapOf("inline_keyboard" to listOf(buttons.toPayload())))
            }
        }
        http.post(
            url("sendMessage"),
            payload,
            TelegramMessageResponse::class.java,
            emptyMap(),
        ).check()
    }

    /**
     * The same [sendMessage], with the keyboard's rows given explicitly: one row per button list. A digest labels its
     * buttons per listing, so they can't share a row.
     */
    open fun sendMessageRows(chatId: Long, text: String, rows: List<List<TelegramButton>>) {
        val keyboard = rows.filter { row -> row.isNotEmpty() }.map { row -> row.toPayload() }
        val payload = buildMap<String, Any> {
            put("chat_id", chatId)
            put("text", text)
            if (keyboard.isNotEmpty()) {
                put("reply_markup", mapOf("inline_keyboard" to keyboard))
            }
        }
        http.post(
            url("sendMessage"),
            payload,
            TelegramMessageResponse::class.java,
            emptyMap(),
        ).check()
    }

    /** Answers the tap of a button: Telegram shows [text] as a toast, once per tap. */
    open fun answerCallbackQuery(callbackQueryId: String, text: String) {
        http.post(
            url("answerCallbackQuery"),
            mapOf("callback_query_id" to callbackQueryId, "text" to text),
            TelegramBooleanResponse::class.java,
            emptyMap(),
        ).check()
    }

    /** Replaces a message's text and its keyboard: the empty default removes the buttons. */
    open fun editMessageText(chatId: Long, messageId: Long, text: String, buttons: List<TelegramButton> = emptyList()) {
        http.post(
            url("editMessageText"),
            mapOf(
                "chat_id" to chatId,
                "message_id" to messageId,
                "text" to text,
                "reply_markup" to mapOf(
                    "inline_keyboard" to if (buttons.isEmpty()) emptyList<List<Any>>() else listOf(buttons.toPayload()),
                ),
            ),
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

    private fun List<TelegramButton>.toPayload(): List<Map<String, String>> = map { button ->
        buildMap {
            put("text", button.text)
            button.callbackData?.let { put("callback_data", it) }
            button.url?.let { put("url", it) }
        }
    }

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
