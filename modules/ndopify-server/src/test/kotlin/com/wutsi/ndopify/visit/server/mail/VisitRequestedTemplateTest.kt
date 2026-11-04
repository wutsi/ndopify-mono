package com.wutsi.ndopify.visit.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the template, which [VisitRequestedMailetTest] doesn't (its resolver is a mock). */
class VisitRequestedTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Kim Possible",
        "customer_name" to "Ray Sponsible",
        "customer_whatsapp" to "+237600000001",
        "listing_title" to "Nice apartment",
        "requested_date" to "01/12/2026",
        "requested_time" to "10:00",
        "note" to "Je viens avec ma femme",
        "visit_url" to "https://partner.ndopify.com/visits/1",
    )

    @Test
    fun render() {
        val html = resolver.resolve(VisitRequestedMailet.TEMPLATE, context)

        assertTrue(html.contains("Kim Possible"))
        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("01/12/2026"))
        assertTrue(html.contains("10:00"))
        assertTrue(html.contains("Je viens avec ma femme"))
        assertTrue(html.contains("https://partner.ndopify.com/visits/1"))
        assertTrue(html.contains("Confirmer la visite"))
    }

    @Test
    fun `without link nor note`() {
        val html = resolver.resolve(
            VisitRequestedMailet.TEMPLATE,
            context - "visit_url" - "note" - "customer_whatsapp",
        )

        assertFalse(html.contains("Confirmer la visite"))
        assertFalse(html.contains("https://partner.ndopify.com/visits/1"))
        assertTrue(html.contains("Nice apartment"))
    }
}
