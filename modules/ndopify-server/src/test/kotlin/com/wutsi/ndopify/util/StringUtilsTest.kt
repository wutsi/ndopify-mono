package com.wutsi.ndopify.util

import kotlin.test.Test
import kotlin.test.assertEquals

class StringUtilsTest {
    @Test
    fun toSlug() {
        assertEquals("/read/123/this-is-a-slug", StringUtils.toSlug("/read/123", "This is a Slug"))
        assertEquals("/read/123/this-is-a-slug", StringUtils.toSlug("/read/123", "This-is a Slug"))
        assertEquals("/read/123/this-is-a-slug", StringUtils.toSlug("/read/123", "This-is a ,Slug"))
        assertEquals("/read/123/this-is-a-slug", StringUtils.toSlug("/read/123", "This.is!a,Slug"))
        assertEquals("/read/123/this-is-a-slug", StringUtils.toSlug("/read/123", "This is a Slug?"))
        assertEquals("/read/123/this-is-a-slug", StringUtils.toSlug("/read/123", "This is a\nSlug?"))
    }

    @Test
    fun toAscii() {
        assertEquals("", StringUtils.toAscii(null))
        assertEquals("", StringUtils.toAscii(""))
        assertEquals("Hello World", StringUtils.toAscii("Hello World"))
        assertEquals("eeeau", StringUtils.toAscii("éèêàü"))
        assertEquals("Francois", StringUtils.toAscii("François"))
        assertEquals("Montreal", StringUtils.toAscii("Montréal"))
        assertEquals("Hello", StringUtils.toAscii("  Hello  "))
    }
}
