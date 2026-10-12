package com.wutsi.ndopify.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.URL
import java.util.UUID

class UrlUtilsTest {
    @Test
    fun `download image`() {
        val file = UrlUtils.download(URL("https://picsum.photos/200/300"))
        assertEquals("jpg", file.extension)
    }

    @Test
    fun `download pdf`() {
        val file = UrlUtils.download(URL("https://pdfobject.com/pdf/sample.pdf"))
        assertEquals("pdf", file.extension)
    }

    @Test
    fun `not found`() {
        assertThrows<IOException> {
            UrlUtils.download(URL("https://file-not-found.com/" + UUID.randomUUID()))
        }
    }
}
