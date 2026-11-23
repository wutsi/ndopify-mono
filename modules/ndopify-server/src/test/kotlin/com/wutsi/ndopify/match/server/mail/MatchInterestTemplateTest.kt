package com.wutsi.ndopify.match.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the template, which [MatchInterestMailetTest] doesn't (its resolver is a mock). */
class MatchInterestTemplateTest {
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
        "price" to "175 000 FCFA / mois",
        "place" to "Bonapriso, Douala",
        "mandate_url" to "https://partner.ndopify.com/mandates/1",
        "whatsapp_url" to "https://wa.me/237600000001",
        "asset" to "un logement",
        "price_label" to "Loyer",
    )

    @Test
    fun `names the customer and the listing`() {
        val html = resolver.resolve(MatchInterestMailet.TEMPLATE, context)

        assertTrue(html.contains("Kim Possible"))
        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("175 000 FCFA / mois"))
        assertTrue(html.contains("Bonapriso, Douala"))
        assertTrue(html.contains("Contactez-le pour organiser la visite"))
        // The listing may be another agent's (co-brokerage): the agent is never told it published it
        assertTrue(html.contains("intéresse à un logement."))
        assertTrue(html.contains("<b>Loyer</b>"))
        assertFalse(html.contains("que vous avez publié"))
        assertTrue(html.contains("src=\"https://cdn.ndopify.com/7.jpg\""))
        assertTrue(html.contains("<a href=\"https://partner.ndopify.com/mandates/1\""))
        assertTrue(html.contains("Voir le mandat"))
        assertTrue(html.contains("<a href=\"https://wa.me/237600000001\""))
        assertTrue(html.contains("Contacter sur WhatsApp"))
    }

    @Test
    fun `a sale listing's interest speaks of a bien and its price`() {
        val html = resolver.resolve(
            MatchInterestMailet.TEMPLATE,
            context + mapOf("asset" to "un bien", "price_label" to "Prix", "price" to "25 000 000 FCFA"),
        )

        assertTrue(html.contains("s'intéresse à un bien"))
        assertTrue(html.contains("<b>Prix</b>"))
        assertFalse(html.contains("<b>Loyer</b>"))
    }

    @Test
    fun `no whatsapp number - no WhatsApp button`() {
        val html = resolver.resolve(MatchInterestMailet.TEMPLATE, context - "whatsapp_url")

        assertFalse(html.contains("wa.me"))
        assertFalse(html.contains("Contacter sur WhatsApp"))
        assertTrue(html.contains("Voir le mandat"))
        assertFalse(html.contains("href=\"\""))
    }

    @Test
    fun `no photo, no mandate page - neither`() {
        val html = resolver.resolve(MatchInterestMailet.TEMPLATE, context - "image_url" - "mandate_url")

        assertFalse(html.contains("cdn.ndopify.com/7.jpg"))
        assertFalse(html.contains("Voir le mandat"))
        assertFalse(html.contains("href=\"\""))
    }
}
