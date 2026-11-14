package com.wutsi.ndopify.visit.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisitCancelledTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Kim Possible",
        "other_party" to "Ray Sponsible",
        "listing_title" to "Nice apartment",
        "visit_date" to "02/12/2026",
        "visit_time" to "10:00",
        "cancelled_by_label" to "Le client a annulé la visite.",
        "cancellation_reason" to "Empêchement",
        "visit_url" to "https://partner.ndopify.com/visits/1",
    )

    @Test
    fun render() {
        val html = resolver.resolve(VisitCancelledMailet.TEMPLATE, context)

        assertTrue(html.contains("Kim Possible"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("annul"))
        assertTrue(html.contains("Contactez Ray Sponsible"))
        assertTrue(html.contains("Empêchement"))
        assertTrue(html.contains("https://partner.ndopify.com/visits/1"))
    }

    @Test
    fun `the customer is told to contact the agent`() {
        val html = resolver.resolve(
            VisitCancelledMailet.TEMPLATE,
            context + mapOf(
                "recipient_name" to "Ray Sponsible",
                "other_party" to "Kim Possible",
                "agent_full_name" to "Kim Possible",
                "cancelled_by_label" to "J'ai annulé la visite.",
            ),
        )

        val text = Jsoup.parse(html).text()

        assertTrue(text.contains("J'ai annulé la visite."))
        assertTrue(text.contains("Contactez-moi"))
        assertFalse(text.contains("Contactez Kim Possible"))
    }

    @Test
    fun `without link nor reason`() {
        val html = resolver.resolve(VisitCancelledMailet.TEMPLATE, context - "visit_url" - "cancellation_reason")

        assertFalse(html.contains("https://partner.ndopify.com/visits/1"))
        assertTrue(html.contains("annul"))
    }
}
