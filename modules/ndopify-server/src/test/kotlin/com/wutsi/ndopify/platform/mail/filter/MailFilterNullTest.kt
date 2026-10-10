package com.wutsi.ndopify.platform.mail.filter

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlEscapeFilterTest {
    val filter = HtmlEscapeFilter()

    @Test
    fun filter() {
        val result = filter.filter("Voilà du <b>français</b> a vacillé!")
        assertEquals("Voil&agrave; du <b>fran&ccedil;ais</b> a vacill&eacute;!", result)
    }

}
