package com.wutsi.ndopify.mandate.server.mail

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

/** Renders the search mandate's assignment e-mail, which the mailet tests don't (their resolver is a mock). */
class MandateAssignedTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Ray Sponsible",
        "place" to "Bastos, Mvan (Yaounde)",
        "agent_full_name" to "Kim Possible",
        "agent_photo_url" to "https://cdn.ndopify.com/agent.jpg",
        "agent_presentation" to "Agent immobilier à Yaoundé",
        "agent_whatsapp" to "+237 6 70 00 00 20",
        "agent_whatsapp_url" to "https://wa.me/237670000020",
        "agent_whatsapp_reply_url" to "https://wa.me/237670000020?text=Bonjour",
        "sale" to false,
    )

    @Test
    fun `the agent introduces itself for a search`() {
        val html = resolver.resolve(MandateAssignedMailet.TEMPLATE, context)
        val text = Jsoup.parse(html).text()

        assertTrue(text.contains("Je prends en charge la recherche de votre bien."))
        assertTrue(text.contains("Je serai votre interlocuteur pour ce mandat."))
        assertTrue(text.contains("Kim Possible"))
        assertTrue(text.contains("Agent immobilier à Yaoundé"))
        assertTrue(text.contains("d'affiner vos critères de recherche"))
        assertFalse(text.contains("votre agent dédié"))
        assertTrue(html.contains("Me répondre sur WhatsApp"))
        assertTrue(html.contains("Envoyé via Ndopify"))
        assertFalse(text.contains("L'équipe Ndopify"))
    }

    @Test
    fun `the photo appears in the footer only`() {
        val doc = Jsoup.parse(resolver.resolve(MandateAssignedMailet.TEMPLATE, context))

        assertEquals(1, doc.select("img[alt='Photo de Kim Possible']").size)
    }

    @Test
    fun `the preview names the place`() {
        val doc = Jsoup.parse(resolver.resolve(MandateAssignedMailet.TEMPLATE, context))
        val preview = assertNotNull(doc.selectFirst("div[style*='display: none']"))

        assertEquals("Votre mandat à Bastos, Mvan (Yaounde)", preview.text())
    }

    @Test
    fun `a sale mandate is presented as a sale`() {
        val html = resolver.resolve(MandateAssignedMailet.TEMPLATE, context + mapOf("sale" to true))
        val text = Jsoup.parse(html).text()

        assertTrue(text.contains("Je prends en charge la vente de votre bien."))
        assertTrue(text.contains("d'organiser une visite de votre bien"))
        assertFalse(text.contains("recherche de votre bien"))
    }
}
