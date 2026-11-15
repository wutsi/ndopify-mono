package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class TelegramWebhookEndpointsTest {
    private val client = mock<TelegramClient>()
    private val handler = TelegramUpdateHandler(client)
    private val mockMvc = MockMvcBuilders
        .standaloneSetup(TelegramWebhookEndpoints(handler, "secret"))
        .build()

    private val body = """{"update_id":1,"message":{"message_id":2,"chat":{"id":123},"text":"hello"}}"""

    @Test
    fun `a valid secret replies OK`() {
        mockMvc.perform(
            post("/telegram/webhook")
                .header("X-Telegram-Bot-Api-Secret-Token", "secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isOk)

        verify(client).sendMessage(123L, "OK")
    }

    @Test
    fun `a missing secret is rejected`() {
        mockMvc.perform(post("/telegram/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)

        verifyNoInteractions(client)
    }

    @Test
    fun `a wrong secret is rejected`() {
        mockMvc.perform(
            post("/telegram/webhook")
                .header("X-Telegram-Bot-Api-Secret-Token", "wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isForbidden)

        verifyNoInteractions(client)
    }

    @Test
    fun `an update without a message is acknowledged without a send`() {
        mockMvc.perform(
            post("/telegram/webhook")
                .header("X-Telegram-Bot-Api-Secret-Token", "secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"update_id":3,"edited_message":{"message_id":4,"chat":{"id":123}}}"""),
        ).andExpect(status().isOk)

        verifyNoInteractions(client)
    }
}
