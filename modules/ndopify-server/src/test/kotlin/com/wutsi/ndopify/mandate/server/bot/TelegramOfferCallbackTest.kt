package com.wutsi.ndopify.mandate.server.bot

import com.wutsi.ndopify.mandate.server.bot.TelegramOfferCallback.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TelegramOfferCallbackTest {
    @Test
    fun `round-trip`() {
        assertEquals(TelegramOfferCallback.Call(Action.ACCEPT, 9L), TelegramOfferCallback.parse("accept:9"))
        assertEquals(TelegramOfferCallback.Call(Action.DECLINE, 9L), TelegramOfferCallback.parse("decline:9"))
        assertEquals("accept:9", TelegramOfferCallback.data(Action.ACCEPT, 9L))
        assertEquals("decline:9", TelegramOfferCallback.data(Action.DECLINE, 9L))
    }

    @Test
    fun `anything else is not a call`() {
        assertNull(TelegramOfferCallback.parse(null))
        assertNull(TelegramOfferCallback.parse(""))
        assertNull(TelegramOfferCallback.parse("accept"))
        assertNull(TelegramOfferCallback.parse("accept:"))
        assertNull(TelegramOfferCallback.parse("accept:abc"))
        assertNull(TelegramOfferCallback.parse("open:9"))
        assertNull(TelegramOfferCallback.parse("accept:9:extra"))
    }
}
