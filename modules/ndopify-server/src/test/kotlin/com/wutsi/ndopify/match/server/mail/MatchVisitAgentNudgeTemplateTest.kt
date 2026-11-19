package com.wutsi.ndopify.match.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the nudge template, which [MatchVisitAgentNudgeMailetTest] doesn't (its resolver is a mock). */
class MatchVisitAgentNudgeTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
        "tenant_name" to "Ndopify",
        "recipient_name" to "Kim Possible",
        "customer_name" to "Ray Sponsible",
        "listing_title" to "Nice apartment",
        "image_url" to "https://cdn.ndopify.com/7.jpg",
        "price_label" to "Loyer",
        "price" to "150 000 FCFA / mois",
        "whatsapp_url" to "https://wa.me/237600000001",
        "visit_url" to "https://partner.ndopify.com/mandates/204#match-9001",
        "agent_full_name" to "Kim Possible",
        "agent_photo_url" to "https://cdn.ndopify.com/agent.jpg",
        "agent_whatsapp" to "+237 6 99 00 00 01",
        "agent_whatsapp_url" to "https://wa.me/237699000001",
    )

    @Test
    fun send() {
        val html = resolver.resolve(MatchVisitAgentNudgeMailet.TEMPLATE, context)

        assertTrue(html.contains("Kim Possible"))
        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("150 000 FCFA / mois"))
        assertTrue(html.contains("Contacter sur WhatsApp"))
        assertTrue(html.contains("https://wa.me/237600000001"))
        assertTrue(html.contains("Planifier la visite"))
        assertTrue(html.contains("https://partner.ndopify.com/mandates/204#match-9001"))
        assertTrue(html.contains("src=\"https://cdn.ndopify.com/agent.jpg\""))
    }

    @Test
    fun `no WhatsApp number - no WhatsApp button`() {
        val html = resolver.resolve(MatchVisitAgentNudgeMailet.TEMPLATE, context - "whatsapp_url")

        assertFalse(html.contains("https://wa.me/237600000001"))
        assertFalse(html.contains("Contacter sur WhatsApp"))
        assertTrue(html.contains("Planifier la visite"))
    }

    @Test
    fun `no Partner Central - no visit button`() {
        val html = resolver.resolve(MatchVisitAgentNudgeMailet.TEMPLATE, context - "visit_url")

        assertFalse(html.contains("/mandates/204#match-9001"))
        assertFalse(html.contains("Planifier la visite"))
        assertTrue(html.contains("Contacter sur WhatsApp"))
    }

    @Test
    fun `no photo - no image`() {
        val html = resolver.resolve(MatchVisitAgentNudgeMailet.TEMPLATE, context - "image_url")

        assertFalse(html.contains("https://cdn.ndopify.com/7.jpg"))
        assertTrue(html.contains("Nice apartment"))
    }
}
