package com.wutsi.ndopify.platform.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals

class MailBodyResolverTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(),
        filters = listOf(MailFilterNull())
    )

    @Test
    fun resolve() {
        val html = resolver.resolve("/mail/test.mjml", mapOf("name" to "Ray Sponsible"))
        assertEquals(true, html.contains("Hello Ray Sponsible"))
    }

    @Test
    fun `not found`() {
        assertThrows<FileNotFoundException> {
            resolver.resolve("/mail/not_found.mjml", mapOf("name" to "Ray Sponsible"))
        }
    }
}
