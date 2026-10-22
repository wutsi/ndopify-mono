package com.wutsi.ndopify.mandate.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the listing mandates' templates, which the mailet tests don't (their resolver is a mock). */
class MandateListingTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Kim Possible",
        "customer_name" to "Ray Sponsible",
        "agent_full_name" to "Kim Possible",
        "contract_number" to "MNDT-2026-000001",
        "listing_type" to "Location",
        "property_type" to "Appartement",
        "city" to "Yaounde",
        "price_label" to "Loyer",
        "price" to "150 000 XAF / mois",
        "area" to 85,
        "available_from" to "01/11/2026",
    )

    @Test
    fun offer() {
        val html = resolver.resolve(MandateOfferMailet.TEMPLATE_LISTING, context)

        assertTrue(html.contains("Un propriétaire souhaite mettre son bien en location"))
        assertTrue(html.contains("Loyer</b>: 150 000 XAF / mois"))
        assertTrue(html.contains("85 m²"))
        assertTrue(html.contains("01/11/2026"))
        assertFalse(html.contains("Budget"))
    }

    @Test
    fun `offer without area nor availability`() {
        val html = resolver.resolve(MandateOfferMailet.TEMPLATE_LISTING, context - "area" - "available_from")

        assertFalse(html.contains("Surface"))
        assertFalse(html.contains("Disponible"))
    }

    @Test
    fun assigned() {
        val html = resolver.resolve(MandateAssignedMailet.TEMPLATE_LISTING, context)

        assertTrue(html.contains("la mise en location de votre bien"))
        assertFalse(html.contains("recherche"))
    }

    @Test
    fun approved() {
        val html = resolver.resolve(MandateApprovedMailet.TEMPLATE_LISTING, context)

        assertTrue(html.contains("a signé le mandat de mise en location"))
        assertTrue(html.contains("Loyer</b>: 150 000 XAF / mois"))
        assertTrue(html.contains("commercialiser le bien"))
        assertFalse(html.contains("Budget"))
    }
}
