package com.wutsi.ndopify.listing.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the template, which [ListingSentBackMailetTest] doesn't (its resolver is a mock). */
class ListingSentBackTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )
    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Kim Possible",
        "property" to "n° 101 — Bastos, Yaoundé",
        "fix_url" to "https://partner.ndopify.com/listings/101",
    )

    @Test
    fun `the e-mail carries the generic message and the fix button`() {
        val html = resolver.resolve(ListingSentBackMailet.TEMPLATE_AGENT, context)

        assertTrue(html.contains("renvoyée pour correction"))
        assertTrue(html.contains("n° 101 — Bastos, Yaoundé"))
        assertTrue(html.contains("Ouvrez-la dans Partner Central"))
        assertTrue(html.contains("https://partner.ndopify.com/listings/101"))
        assertTrue(html.contains("Corriger l'annonce"))
    }

    @Test
    fun `without a link there is no button`() {
        val html = resolver.resolve(ListingSentBackMailet.TEMPLATE_AGENT, context - "fix_url")

        assertFalse(html.contains("partner.ndopify.com"))
        assertFalse(html.contains("Corriger l'annonce"))
    }
}
