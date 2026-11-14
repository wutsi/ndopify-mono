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

/**
 * Renders the templates, which [CoBrokerageVisitMailetTest] doesn't (its resolver is a mock). Never a customer field:
 * the context has none, and the leak assertions prove the copy can't render one.
 */
class CoBrokerageVisitTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "John Doe",
        "listing_title" to "Villa Bastos",
        "visit_date" to "02/12/2026",
        "visit_time" to "10:00",
        "type_label" to "Sur place",
        "on_site" to true,
        "cancelled_by_label" to "J'ai annulé la visite.",
        "reason_label" to "Le client ne s'est pas présenté à la visite.",
        "agent_full_name" to "Kim Possible",
        "agent_profile_url" to "https://partner.ndopify.com/agent?id=20",
        "agent_whatsapp_url" to "https://wa.me/237699000001",
        "listing_url" to "https://partner.ndopify.com/listings/101#visits-title",
    )

    @Test
    fun confirmed() {
        val html = resolver.resolve(CoBrokerageVisitMailet.TEMPLATE_CONFIRMED, context)

        assertCommon(html)
        assertTrue(html.contains("J'ai organisé une visite de votre bien"))
        assertTrue(html.contains("Je vous y retrouve avec mon client"))
        assertFalse(html.contains("suivra en visio"))
    }

    @Test
    fun `confirmed - video`() {
        val html = resolver.resolve(
            CoBrokerageVisitMailet.TEMPLATE_CONFIRMED,
            context + mapOf("on_site" to false, "type_label" to "Visite vidéo"),
        )

        assertTrue(html.contains("suivra en visio"))
        assertTrue(html.contains("Visite vidéo"))
        assertFalse(html.contains("Je vous y retrouve avec mon client"))
    }

    @Test
    fun reminder() {
        val html = resolver.resolve(CoBrokerageVisitMailet.TEMPLATE_REMINDER, context)

        assertCommon(html)
        assertTrue(html.contains("Rappel"))
        assertTrue(html.contains("a lieu"))
        assertFalse(html.contains("suivra en visio"))
    }

    @Test
    fun `reminder - video`() {
        val html = resolver.resolve(
            CoBrokerageVisitMailet.TEMPLATE_REMINDER,
            context + mapOf("on_site" to false, "type_label" to "Visite vidéo"),
        )

        assertTrue(html.contains("suivra en visio"))
        assertTrue(html.contains("Visite vidéo"))
    }

    @Test
    fun cancelled() {
        val html = resolver.resolve(CoBrokerageVisitMailet.TEMPLATE_CANCELLED, context)

        assertCommon(html)
        val text = Jsoup.parse(html).text()
        assertTrue(text.contains("J'ai annulé la visite."))
        assertTrue(text.contains("a été annulée"))
        assertFalse(text.contains("cancellation_reason"))
        assertFalse(text.contains("Mandat clôturé"))
    }

    @Test
    fun `no show`() {
        val html = resolver.resolve(CoBrokerageVisitMailet.TEMPLATE_NO_SHOW, context)

        assertCommon(html)
        val text = Jsoup.parse(html).text()
        assertTrue(text.contains("Le client ne s'est pas présenté à la visite."))
        assertTrue(text.contains("n'a pas eu lieu"))
    }

    private fun assertCommon(html: String) {
        assertTrue(html.contains("Villa Bastos"))
        assertTrue(html.contains("Kim Possible"))
        assertTrue(html.contains("https://wa.me/237699000001"))
        assertTrue(html.contains("https://partner.ndopify.com/listings/101#visits-title"))
        assertFalse(html.contains("Secret Client"))
        assertFalse(html.contains("secret@example.com"))
        assertFalse(html.contains("1234"))
        assertFalse(html.contains("Client pressé"))
    }
}
