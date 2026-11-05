package com.wutsi.ndopify.web.ui.layout

import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortalLinkTest {
    @Test
    fun `token shape`() {
        assertTrue(PortalLink.isToken("aaa.bbb.ccc"))
        listOf("abc", "a.b", "a.b.c.d", "a.b;c.d", "a.b c.d", "a.\"b.c", "a.b,c.d").forEach {
            assertFalse(PortalLink.isToken(it), it)
        }
    }

    @Test
    fun `strips only the token`() {
        val request = MockHttpServletRequest("GET", "/visits/9")
        request.addParameter("token", "aaa.bbb.ccc")
        request.addParameter("cancel", "done")

        assertEquals("/visits/9?cancel=done", PortalLink.withoutToken(request))
    }
}
