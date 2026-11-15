package com.wutsi.ndopify.platform.telegram.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The `ok` / `description` pair every Bot API answer carries: a call answered `ok: false` is a failure the client
 * turns into a [com.wutsi.ndopify.platform.telegram.TelegramException].
 */
interface TelegramResponse {
    val ok: Boolean
    val description: String?
}

data class TelegramUpdate(
    @get:JsonProperty("update_id") val updateId: Long = 0,
    @get:JsonProperty("message") val message: TelegramMessage? = null,
)

data class TelegramMessage(
    @get:JsonProperty("message_id") val messageId: Long = 0,
    @get:JsonProperty("chat") val chat: TelegramChat = TelegramChat(),
    @get:JsonProperty("text") val text: String? = null,
)

data class TelegramChat(
    @get:JsonProperty("id") val id: Long = 0,
)

data class TelegramUser(
    @get:JsonProperty("id") val id: Long = 0,
    @get:JsonProperty("is_bot") val isBot: Boolean = false,
    @get:JsonProperty("first_name") val firstName: String = "",
    @get:JsonProperty("username") val username: String? = null,
)

data class TelegramUserResponse(
    override val ok: Boolean = false,
    @get:JsonProperty("result") val result: TelegramUser? = null,
    override val description: String? = null,
) : TelegramResponse

data class TelegramUpdatesResponse(
    override val ok: Boolean = false,
    @get:JsonProperty("result") val result: List<TelegramUpdate>? = null,
    override val description: String? = null,
) : TelegramResponse

data class TelegramMessageResponse(
    override val ok: Boolean = false,
    @get:JsonProperty("result") val result: TelegramMessage? = null,
    override val description: String? = null,
) : TelegramResponse

data class TelegramBooleanResponse(
    override val ok: Boolean = false,
    @get:JsonProperty("result") val result: Boolean? = null,
    override val description: String? = null,
) : TelegramResponse
