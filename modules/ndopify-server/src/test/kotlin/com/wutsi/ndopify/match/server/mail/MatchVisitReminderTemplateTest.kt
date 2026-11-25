package com.wutsi.ndopify.match.server.mail

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

/** Renders the template, which [MatchVisitReminderMailetTest] doesn't (its resolver is a mock). */
class MatchVisitReminderTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Ray Sponsible",
        "agent_full_name" to "Kim Possible",
        "agent_photo_url" to "https://cdn.ndopify.com/agent.jpg",
        "agent_whatsapp" to "+237 6 99 00 00 01",
        "agent_whatsapp_url" to "https://wa.me/237699000001",
        "agent_whatsapp_reply_url" to "https://wa.me/237699000001?text=Bonjour",
        "listing_title" to "Nice apartment",
        "image_url" to "https://cdn.ndopify.com/7.jpg",
        "price_label" to "Loyer",
        "price" to "150 000 FCFA / mois",
        "online" to true,
        "listing_url" to "https://www.ndopify.com/match/1",
    )

    @Test
    fun `asks for a visit`() {
        val html = resolver.resolve(MatchVisitReminderMailet.TEMPLATE, context)

        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("Vous avez indiqué que ce bien vous intéresse"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("150 000 FCFA / mois"))
        assertTrue(html.contains("https://www.ndopify.com/match/1"))
        assertTrue(html.contains("Demander une visite"))
        assertFalse(html.contains("Répondez-moi"))
        assertTrue(html.contains("src=\"https://cdn.ndopify.com/7.jpg\""))
        // The shared agent footer: photo, name, WhatsApp
        assertTrue(html.contains("https://wa.me/237699000001"))
        assertTrue(html.contains("src=\"https://cdn.ndopify.com/agent.jpg\""))
    }

    @Test
    fun `no photo - no image`() {
        val html = resolver.resolve(MatchVisitReminderMailet.TEMPLATE, context - "image_url")

        assertTrue(html.contains("Nice apartment"))
        assertFalse(html.contains("cdn.ndopify.com/7.jpg"))
    }

    @Test
    fun `no link - reply on WhatsApp`() {
        val html = resolver.resolve(MatchVisitReminderMailet.TEMPLATE, context - "listing_url" - "online")

        assertFalse(html.contains("Demander une visite"))
        assertTrue(html.contains("Écrivez-moi sur WhatsApp"))
        assertFalse(html.contains("Répondez-moi"))
        assertFalse(html.contains("href=\"\""))
    }

    @Test
    fun `the copy speaks as the agent, replies on WhatsApp`() {
        val html = resolver.resolve(MatchVisitReminderMailet.TEMPLATE, context)
        val text = Jsoup.parse(html).text()

        assertTrue(html.contains("Me répondre sur WhatsApp"))
        assertTrue(html.contains("Envoyé via Ndopify"))
        assertFalse(text.contains("Votre agent"))
        assertFalse(text.contains("L'équipe Ndopify"))
    }

    @Test
    fun `the preview carries the event`() {
        val preview = assertNotNull(
            Jsoup.parse(resolver.resolve(MatchVisitReminderMailet.TEMPLATE, context))
                .selectFirst("div[style*='display: none']"),
        )

        assertEquals("Nice apartment · Loyer 150 000 FCFA / mois", preview.text())
    }
}
