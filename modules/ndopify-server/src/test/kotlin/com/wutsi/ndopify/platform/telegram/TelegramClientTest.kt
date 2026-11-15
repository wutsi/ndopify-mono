package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.platform.telegram.model.TelegramBooleanResponse
import com.wutsi.ndopify.platform.telegram.model.TelegramMessageResponse
import com.wutsi.ndopify.platform.telegram.model.TelegramUpdate
import com.wutsi.ndopify.platform.telegram.model.TelegramUpdatesResponse
import com.wutsi.ndopify.platform.telegram.model.TelegramUser
import com.wutsi.ndopify.platform.telegram.model.TelegramUserResponse
import com.wutsi.ndopify.platform.util.http.Http
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TelegramClientTest {
    private val http = mock<Http>()
    private val client = TelegramClient("123:token", http)
    private val base = "https://api.telegram.org/bot123:token"

    @Test
    fun sendMessage() {
        doReturn(TelegramMessageResponse(ok = true))
            .whenever(http).post(
                eq("$base/sendMessage"),
                eq(mapOf("chat_id" to 123L, "text" to "OK")),
                eq(TelegramMessageResponse::class.java),
                eq(emptyMap()),
            )

        client.sendMessage(123L, "OK")

        verify(http).post(
            eq("$base/sendMessage"),
            eq(mapOf("chat_id" to 123L, "text" to "OK")),
            eq(TelegramMessageResponse::class.java),
            eq(emptyMap()),
        )
    }

    @Test
    fun getMe() {
        doReturn(TelegramUserResponse(ok = true, result = TelegramUser(id = 1L, username = "ndopify_bot")))
            .whenever(http).get(eq("$base/getMe"), eq(TelegramUserResponse::class.java), eq(emptyMap()))

        assertEquals("ndopify_bot", client.getMe().username)
    }

    @Test
    fun getUpdates() {
        doReturn(
            TelegramUpdatesResponse(
                ok = true,
                result = listOf(TelegramUpdate(updateId = 10L), TelegramUpdate(updateId = 11L)),
            ),
        ).whenever(http).get(
            eq("$base/getUpdates?offset=9&timeout=20"),
            eq(TelegramUpdatesResponse::class.java),
            eq(emptyMap()),
        )

        assertEquals(listOf(10L, 11L), client.getUpdates(9L, 20).map { it.updateId })
    }

    @Test
    fun setWebhook() {
        doReturn(TelegramBooleanResponse(ok = true))
            .whenever(http).post(
                eq("$base/setWebhook"),
                eq(mapOf("url" to "https://host/telegram/webhook", "secret_token" to "secret")),
                eq(TelegramBooleanResponse::class.java),
                eq(emptyMap()),
            )

        client.setWebhook("https://host/telegram/webhook", "secret")

        verify(http).post(
            eq("$base/setWebhook"),
            eq(mapOf("url" to "https://host/telegram/webhook", "secret_token" to "secret")),
            eq(TelegramBooleanResponse::class.java),
            eq(emptyMap()),
        )
    }

    @Test
    fun `deleteWebhook posts an empty payload`() {
        doReturn(TelegramBooleanResponse(ok = true))
            .whenever(http).post(
                eq("$base/deleteWebhook"),
                eq(emptyMap<String, Any>()),
                eq(TelegramBooleanResponse::class.java),
                eq(emptyMap()),
            )

        client.deleteWebhook()

        verify(http).post(
            eq("$base/deleteWebhook"),
            eq(emptyMap<String, Any>()),
            eq(TelegramBooleanResponse::class.java),
            eq(emptyMap()),
        )
    }

    @Test
    fun `a refused call throws the description without the token`() {
        doReturn(TelegramUserResponse(ok = false, description = "Unauthorized"))
            .whenever(http).get(eq("$base/getMe"), eq(TelegramUserResponse::class.java), eq(emptyMap()))

        val ex = assertFailsWith<TelegramException> { client.getMe() }

        assertEquals("Unauthorized", ex.message)
        assertFalse(ex.message!!.contains("123:token"))
    }
}
