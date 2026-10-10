package com.wutsi.ndopify.platform.mail.filter

import kotlin.test.Test
import kotlin.test.assertEquals

class MailFilterNullTest {
    val filter = MailFilterNull()

    @Test
    fun filter() {
        val result = filter.filter("Voilà du <b>français</b> a vacillé!")
        assertEquals("Voilà du <b>français</b> a vacillé!", result)
    }
}
