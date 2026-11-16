package com.wutsi.ndopify.match.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the template, which [MatchAnsweredMailetTest] doesn't (its resolver is a mock). */
class MatchAnsweredTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Kim Possible",
        "customer_name" to "Ray Sponsible",
        "accepted" to true,
        "listing_title" to "Small apartment",
        "price_label" to "Loyer",
        "price" to "140 000 FCFA / mois",
        "listing_type" to "Location",
        "property_type" to "Appartement",
        "city" to "Yaounde",
        "neighbourhoods" to "Bastos",
        "budget" to "150 000 FCFA / mois",
        "mandate_url" to "https://partner.ndopify.com/mandates/1",
    )

    @Test
    fun `an acceptance`() {
        val html = resolver.resolve(MatchAnsweredMailet.TEMPLATE, context)

        assertTrue(html.contains("Bonne nouvelle"))
        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("a accepté la proposition"))
        assertTrue(html.contains("Small apartment"))
        assertTrue(html.contains("140 000 FCFA / mois"))
        assertTrue(html.contains("Location - Appartement"))
        assertTrue(html.contains("Yaounde"))
        assertTrue(html.contains("Bastos"))
        assertTrue(html.contains("150 000 FCFA / mois"))
        assertTrue(html.contains("Cette réponse est définitive"))
        assertTrue(html.contains("https://partner.ndopify.com/mandates/1"))
        assertTrue(html.contains("Voir le mandat"))
        assertFalse(html.contains("Motif"))
    }

    @Test
    fun `a rejection`() {
        val html = resolver.resolve(
            MatchAnsweredMailet.TEMPLATE,
            context - "accepted" + mapOf("rejection_reason" to "trop cher", "comment" to "Trop loin du travail."),
        )

        assertTrue(html.contains("a refusé la proposition"))
        assertTrue(html.contains("trop cher"))
        assertTrue(html.contains("Trop loin du travail."))
        assertFalse(html.contains("Bonne nouvelle"))
    }

    @Test
    fun `no mandate link - no button`() {
        val html = resolver.resolve(MatchAnsweredMailet.TEMPLATE, context - "mandate_url")

        assertFalse(html.contains("Voir le mandat"))
        assertFalse(html.contains("href=\"\""))
    }
}
