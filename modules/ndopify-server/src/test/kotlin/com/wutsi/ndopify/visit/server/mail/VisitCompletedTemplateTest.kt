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

class VisitCompletedTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Ray Sponsible",
        "listing_title" to "Nice apartment",
        "visit_date" to "02/12/2026",
        "visit_time" to "10:00",
        "visit_url" to "https://www.ndopify.com/visits/1?token=xxx.yyy.zzz",
        "agent_full_name" to "Kim Possible",
        "agent_whatsapp" to "+237 6 99 00 00 01",
        "agent_whatsapp_url" to "https://wa.me/237699000001",
        "agent_whatsapp_reply_url" to "https://wa.me/237699000001?text=Bonjour",
        "agent_customer_email" to true,
    )

    @Test
    fun render() {
        val html = resolver.resolve(VisitCompletedMailet.TEMPLATE, context)

        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("Notre visite du bien"))
        assertTrue(html.contains("Alors, ce bien vous int"))
        assertTrue(html.contains("Dites-le-moi"))
        assertTrue(html.contains("je vous accompagnerai"))
        assertFalse(html.contains("Dites-le à votre agent"))
        assertTrue(html.contains("https://www.ndopify.com/visits/1?token=xxx.yyy.zzz"))
    }

    @Test
    fun `the copy speaks as the agent, replies on WhatsApp`() {
        val html = resolver.resolve(VisitCompletedMailet.TEMPLATE, context)

        assertTrue(html.contains("Me répondre sur WhatsApp"))
        assertTrue(html.contains("Envoyé via Ndopify"))
        assertFalse(html.contains("Votre agent"))
        assertFalse(html.contains("L'équipe Ndopify"))
    }

    @Test
    fun `the preview carries the event`() {
        val doc = Jsoup.parse(resolver.resolve(VisitCompletedMailet.TEMPLATE, context))
        val preview = assertNotNull(doc.selectFirst("div[style*='display: none']"))

        assertEquals("02/12/2026 · Nice apartment", preview.text())
    }

    @Test
    fun `without link`() {
        val html = resolver.resolve(VisitCompletedMailet.TEMPLATE, context - "visit_url")

        assertFalse(html.contains(" https://www.ndopify.com/visits/1"))
        assertTrue(html.contains("Alors, ce bien vous int"))
    }
}
