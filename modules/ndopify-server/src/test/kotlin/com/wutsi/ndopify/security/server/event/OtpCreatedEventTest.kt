package com.wutsi.ndopify.security.server.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OtpCreatedEventTest {
    @Test
    fun `toString masks the code`() {
        val event = OtpCreatedEvent(authFactorId = 1L, code = "123456", tenantId = 2L)

        assertFalse(event.toString().contains("123456"), "the clear code must never be logged")
        assertEquals("OtpCreatedEvent(authFactorId=1, code=******, tenantId=2)", event.toString())
    }
}
