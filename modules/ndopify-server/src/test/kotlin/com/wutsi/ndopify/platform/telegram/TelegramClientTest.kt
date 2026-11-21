package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.platform.telegram.model.TelegramBooleanResponse
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
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
    fun `sendMessage with buttons posts one keyboard row per button`() {
        doReturn(TelegramMessageResponse(ok = true))
            .whenever(http).post(eq("$base/sendMessage"), any(), eq(TelegramMessageResponse::class.java), eq(emptyMap()))

        client.sendMessage(
            123L,
            "Offre",
            listOf(
                TelegramButton("Accepter", "accept:9"),
                TelegramButton("Refuser", "decline:9"),
                TelegramButton("Voir le mandat", "view:9"),
            ),
        )

        verify(http).post(
            eq("$base/sendMessage"),
            eq(
                mapOf(
                    "chat_id" to 123L,
                    "text" to "Offre",
                    "reply_markup" to mapOf(
                        "inline_keyboard" to listOf(
                            listOf(mapOf("text" to "Accepter", "callback_data" to "accept:9")),
                            listOf(mapOf("text" to "Refuser", "callback_data" to "decline:9")),
                            listOf(mapOf("text" to "Voir le mandat", "callback_data" to "view:9")),
                        ),
                    ),
                ),
            ),
            eq(TelegramMessageResponse::class.java),
            eq(emptyMap()),
        )
    }

    @Test
    fun `sendMessageRows keeps a row's buttons together`() {
        doReturn(TelegramMessageResponse(ok = true))
            .whenever(http).post(eq("$base/sendMessage"), any(), eq(TelegramMessageResponse::class.java), eq(emptyMap()))

        client.sendMessageRows(
            123L,
            "Digest",
            listOf(
                listOf(TelegramButton("Accepter", "accept:9"), TelegramButton("Refuser", "decline:9")),
                listOf(TelegramButton("Voir le mandat", url = "https://pc.example/mandates/11")),
            ),
        )

        verify(http).post(
            eq("$base/sendMessage"),
            eq(
                mapOf(
                    "chat_id" to 123L,
                    "text" to "Digest",
                    "reply_markup" to mapOf(
                        "inline_keyboard" to listOf(
                            listOf(
                                mapOf("text" to "Accepter", "callback_data" to "accept:9"),
                                mapOf("text" to "Refuser", "callback_data" to "decline:9"),
                            ),
                            listOf(mapOf("text" to "Voir le mandat", "url" to "https://pc.example/mandates/11")),
                        ),
                    ),
                ),
            ),
            eq(TelegramMessageResponse::class.java),
            eq(emptyMap()),
        )
    }

    @Test
    fun `sendMessageRows posts one keyboard row per button`() {
        doReturn(TelegramMessageResponse(ok = true))
            .whenever(http).post(eq("$base/sendMessage"), any(), eq(TelegramMessageResponse::class.java), eq(emptyMap()))

        client.sendMessageRows(
            123L,
            "Digest",
            listOf(
                listOf(TelegramButton("Voir le mandat — Villa Bastos", url = "https://pc.example/mandates/11")),
                listOf(TelegramButton("Voir le mandat — Studio Mvan", url = "https://pc.example/mandates/12")),
            ),
        )

        verify(http).post(
            eq("$base/sendMessage"),
            eq(
                mapOf(
                    "chat_id" to 123L,
                    "text" to "Digest",
                    "reply_markup" to mapOf(
                        "inline_keyboard" to listOf(
                            listOf(
                                mapOf("text" to "Voir le mandat — Villa Bastos", "url" to "https://pc.example/mandates/11"),
                            ),
                            listOf(
                                mapOf("text" to "Voir le mandat — Studio Mvan", "url" to "https://pc.example/mandates/12"),
                            ),
                        ),
                    ),
                ),
            ),
            eq(TelegramMessageResponse::class.java),
            eq(emptyMap()),
        )
    }

    @Test
    fun `sendMessageRows without rows posts no keyboard`() {
        doReturn(TelegramMessageResponse(ok = true))
            .whenever(http).post(eq("$base/sendMessage"), any(), eq(TelegramMessageResponse::class.java), eq(emptyMap()))

        client.sendMessageRows(123L, "Digest", emptyList())

        verify(http).post(
            eq("$base/sendMessage"),
            eq(mapOf("chat_id" to 123L, "text" to "Digest")),
            eq(TelegramMessageResponse::class.java),
            eq(emptyMap()),
        )
    }

    @Test
    fun answerCallbackQuery() {
        doReturn(TelegramBooleanResponse(ok = true))
            .whenever(http).post(eq("$base/answerCallbackQuery"), any(), eq(TelegramBooleanResponse::class.java), eq(emptyMap()))

        client.answerCallbackQuery("cb-1", "Mandat accepté.")

        verify(http).post(
            eq("$base/answerCallbackQuery"),
            eq(mapOf("callback_query_id" to "cb-1", "text" to "Mandat accepté.")),
            eq(TelegramBooleanResponse::class.java),
            eq(emptyMap()),
        )
    }

    @Test
    fun `editMessageText clears the keyboard`() {
        doReturn(TelegramMessageResponse(ok = true))
            .whenever(http).post(eq("$base/editMessageText"), any(), eq(TelegramMessageResponse::class.java), eq(emptyMap()))

        client.editMessageText(123L, 50L, "Mandat accepté.")

        verify(http).post(
            eq("$base/editMessageText"),
            eq(
                mapOf(
                    "chat_id" to 123L,
                    "message_id" to 50L,
                    "text" to "Mandat accepté.",
                    "reply_markup" to mapOf("inline_keyboard" to emptyList<List<TelegramButton>>()),
                ),
            ),
            eq(TelegramMessageResponse::class.java),
            eq(emptyMap()),
        )
    }

    @Test
    fun `editMessageText with buttons posts one keyboard row per button`() {
        doReturn(TelegramMessageResponse(ok = true))
            .whenever(http).post(eq("$base/editMessageText"), any(), eq(TelegramMessageResponse::class.java), eq(emptyMap()))

        client.editMessageText(
            123L,
            50L,
            "Mandat accepté.",
            listOf(
                TelegramButton("Voir le mandat", url = "https://npc.example/mandates/10"),
                TelegramButton("Voir la visite", url = "https://npc.example/visits/2"),
            ),
        )

        verify(http).post(
            eq("$base/editMessageText"),
            eq(
                mapOf(
                    "chat_id" to 123L,
                    "message_id" to 50L,
                    "text" to "Mandat accepté.",
                    "reply_markup" to mapOf(
                        "inline_keyboard" to listOf(
                            listOf(mapOf("text" to "Voir le mandat", "url" to "https://npc.example/mandates/10")),
                            listOf(mapOf("text" to "Voir la visite", "url" to "https://npc.example/visits/2")),
                        ),
                    ),
                ),
            ),
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

    @Test
    fun `sendPhoto posts the image, its caption and one keyboard row per button`() {
        val bytes = byteArrayOf(1, 2, 3)
        doReturn(TelegramMessageResponse(ok = true))
            .whenever(http).postMultipart(
                eq("$base/sendPhoto"),
                eq(
                    mapOf(
                        "chat_id" to 123L,
                        "caption" to "En ligne",
                        "reply_markup" to mapOf(
                            "inline_keyboard" to listOf(
                                listOf(mapOf("text" to "Voir l'annonce", "url" to "https://www.ndopify.com/listing")),
                                listOf(mapOf("text" to "Voir le mandat", "url" to "https://npc.example/mandates/10")),
                            ),
                        ),
                    ),
                ),
                argThat {
                    fieldName == "photo" &&
                        filename == "1.jpg" &&
                        contentType == "image/jpeg" &&
                        content.contentEquals(bytes)
                },
                eq(TelegramMessageResponse::class.java),
                eq(emptyMap()),
            )

        client.sendPhoto(
            123L,
            bytes,
            "1.jpg",
            "image/jpeg",
            "En ligne",
            listOf(
                TelegramButton("Voir l'annonce", url = "https://www.ndopify.com/listing"),
                TelegramButton("Voir le mandat", url = "https://npc.example/mandates/10"),
            ),
        )

        verify(http).postMultipart(
            eq("$base/sendPhoto"),
            any(),
            argThat { content.contentEquals(bytes) },
            eq(TelegramMessageResponse::class.java),
            eq(emptyMap()),
        )
    }
}
