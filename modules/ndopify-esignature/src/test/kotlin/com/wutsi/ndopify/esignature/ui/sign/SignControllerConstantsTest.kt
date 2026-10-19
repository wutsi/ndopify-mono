package com.wutsi.ndopify.esignature.ui.sign

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class SignControllerConstantsTest {
    @Test
    fun codeIsPendingForFiveMinutes() {
        assertEquals(Duration.ofMinutes(5), SignController.OTP_TTL)
    }
}
