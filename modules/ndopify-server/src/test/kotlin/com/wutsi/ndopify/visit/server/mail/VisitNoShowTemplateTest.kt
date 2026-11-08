package com.wutsi.ndopify.visit.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisitNoShowTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Ray Sponsible",
        "other_party" to "Kim Possible",
        "listing_title" to "Nice apartment",
        "visit_date" to "02/12/2026",
        "visit_time" to "10:00",
        "reason_label" to "Le client ne s'est pas présenté.",
        "visit_url" to "https://www.ndopify.com/visits/1?token=xxx.yyy.zzz",
    )

    @Test
    fun render() {
        val html = resolver.resolve(VisitNoShowMailet.TEMPLATE, context)

        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("Le client ne s"))
        assertTrue(html.contains("Demander une autre visite"))
        assertTrue(html.contains("https://www.ndopify.com/visits/1?token=xxx.yyy.zzz"))
    }

    @Test
    fun `the agent is told to contact the customer`() {
        val html = resolver.resolve(
            VisitNoShowMailet.TEMPLATE,
            context + mapOf("other_party" to "Ray Sponsible", "agent_recipient" to true),
        )

        assertTrue(html.contains("Contactez votre client"))
        assertFalse(html.contains("demander une nouvelle visite"))
    }

    @Test
    fun `without link`() {
        val html = resolver.resolve(VisitNoShowMailet.TEMPLATE, context - "visit_url")

        assertFalse(html.contains("Demander une autre visite"))
    }
}
