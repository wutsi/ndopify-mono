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
        "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
    )

    private val customer = context + mapOf(
        "recipient_name" to "Ray Sponsible",
        "other_party" to "Kim Possible",
        "agent_full_name" to "Kim Possible",
        "agent_whatsapp" to "+237699000001",
        "agent_whatsapp_url" to "https://wa.me/237699000001",
        "agent_whatsapp_reply_url" to "https://wa.me/237699000001?text=Bonjour",
        "cancelled_by_label" to "J'ai annulé la visite.",
        "visit_url" to "https://www.ndopify.com/visits/1?token=xxx.yyy.zzz",
        "agent_customer_email" to true,
    )

    @Test
    fun `the customer reads the cancellation as the agent's`() {
        val html = resolver.resolve(VisitCancelledMailet.TEMPLATE, customer)
        val text = Jsoup.parse(html).text()

        assertTrue(text.contains("Je dois annuler notre visite du bien"))
        assertTrue(text.contains("prévue le 02/12/2026 à 10:00"))
        assertTrue(text.contains("Motif : Empêchement"))
        assertFalse(text.contains("La visite du bien"))
        assertFalse(text.contains("Votre agent"))
        assertFalse(text.contains("L'équipe Ndopify"))
        assertTrue(html.contains("Me répondre sur WhatsApp"))
        assertTrue(html.contains("Envoyé via Ndopify"))
        assertTrue(text.contains("Cordialement, Kim Possible WhatsAp"))
        assertFalse(html.contains("alt=\"Logo\""), "the customer copy keeps the divider, never the tenant logo")
    }

    @Test
    fun `the agent copy keeps the tenant voice`() {
        val html = resolver.resolve(VisitCancelledMailet.TEMPLATE, context)
        val text = Jsoup.parse(html).text()

        assertTrue(text.contains("La visite du bien Nice apartment"))
        assertTrue(text.contains("Le client a annulé la visite."))
        assertTrue(text.contains("Contactez Ray Sponsible"))
        assertTrue(text.contains("Cordialement, L'équipe Ndopify"))
        assertFalse(html.contains("Me répondre sur WhatsApp"))
        assertFalse(html.contains("Je dois annuler"))
        assertTrue(html.contains("alt=\"Logo\""), "the agent copy keeps today's tenant logo")
    }

    @Test
    fun `the customer copy's preview carries the event`() {
        val doc = Jsoup.parse(resolver.resolve(VisitCancelledMailet.TEMPLATE, customer))
        val preview = assertNotNull(doc.selectFirst("div[style*='display: none']"))

        assertEquals("02/12/2026 à 10:00 · Nice apartment", preview.text())
    }

    @Test
    fun `without link nor reason`() {
        val html = resolver.resolve(VisitCancelledMailet.TEMPLATE, context - "visit_url" - "cancellation_reason")

        assertFalse(html.contains("https://partner.ndopify.com/visits/1"))
        assertTrue(html.contains("annul"))
    }

    @Test
    fun `the system cancellation does not invite another date`() {
        val html = resolver.resolve(
            VisitCancelledMailet.TEMPLATE,
            context + mapOf(
                "system_cancelled" to true,
                "cancelled_by_label" to "La visite est annulée.",
            ),
        )

        val text = Jsoup.parse(html).text()

        assertTrue(text.contains("La visite est annulée."))
        assertFalse(text.contains("convenir d'une autre date"))
    }
}
