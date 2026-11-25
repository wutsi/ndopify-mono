package com.wutsi.ndopify.visit.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        "reason_label" to "Vous ne vous êtes pas présenté.",
        "visit_url" to "https://www.ndopify.com/visits/1?token=xxx.yyy.zzz",
        "agent_full_name" to "Kim Possible",
        "agent_whatsapp" to "+237 6 99 00 00 01",
        "agent_whatsapp_url" to "https://wa.me/237699000001",
        "agent_whatsapp_reply_url" to "https://wa.me/237699000001?text=Bonjour",
        "agent_customer_email" to true,
        "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
    )

    @Test
    fun render() {
        val html = resolver.resolve(VisitNoShowMailet.TEMPLATE, context)

        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("Vous ne vous êtes pas présenté"))
        assertTrue(html.contains("je l'organiserai"))
        assertTrue(html.contains("Demander une autre visite"))
        assertTrue(html.contains("https://www.ndopify.com/visits/1?token=xxx.yyy.zzz"))
        assertTrue(html.contains("Me répondre sur WhatsApp"))
        assertTrue(html.contains("Envoyé via Ndopify"))
        assertFalse(html.contains("Votre agent"))
        assertFalse(html.contains("alt=\"Logo\""), "the customer copy keeps the divider, never the tenant logo")
    }

    @Test
    fun `the agent is told to contact the customer`() {
        val html = resolver.resolve(
            VisitNoShowMailet.TEMPLATE,
            context - "agent_full_name" - "agent_whatsapp_reply_url" - "agent_whatsapp" - "agent_whatsapp_url" -
                "agent_customer_email" +
                mapOf(
                    "other_party" to "Ray Sponsible",
                    "reason_label" to "Le client ne s'est pas présenté.",
                    "agent_recipient" to true,
                ),
        )

        assertTrue(html.contains("Contactez votre client"))
        assertFalse(html.contains("demander une nouvelle visite"))
        assertFalse(html.contains("Me répondre sur WhatsApp"))
        assertTrue(html.contains("alt=\"Logo\""), "the agent copy keeps today's tenant logo")
    }

    @Test
    fun `the customer copy's preview carries the event`() {
        val doc = Jsoup.parse(resolver.resolve(VisitNoShowMailet.TEMPLATE, context))
        val preview = assertNotNull(doc.selectFirst("div[style*='display: none']"))

        assertEquals("02/12/2026 · Nice apartment", preview.text())
    }

    @Test
    fun `without link`() {
        val html = resolver.resolve(VisitNoShowMailet.TEMPLATE, context - "visit_url")

        assertFalse(html.contains("Demander une autre visite"))
    }
}
