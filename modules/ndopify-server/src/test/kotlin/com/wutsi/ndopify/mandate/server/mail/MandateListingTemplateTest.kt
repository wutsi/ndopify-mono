package com.wutsi.ndopify.mandate.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
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
        mustache = DefaultMustacheFactory(ClasspathResolver()),
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

    // A SALE listing's context: Vente, its price, and the sale flag the templates switch on
    private val saleContext = context + mapOf(
        "listing_type" to "Vente",
        "price_label" to "Prix",
        "price" to "25 000 000 XAF",
        "sale" to true,
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
    fun `offer of a sale - the owner wants to sell`() {
        val html = resolver.resolve(MandateOfferMailet.TEMPLATE_LISTING, saleContext)

        assertTrue(html.contains("Un propriétaire souhaite vendre son bien"))
        assertTrue(html.contains("Prix</b>: 25 000 000 XAF"))
        assertFalse(html.contains("mettre son bien en location"))
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
        assertTrue(html.contains("Je suis votre agent dédié"))
        assertTrue(html.contains("Je me présente"))
        assertTrue(html.contains("Je vous contacterai"))
        assertFalse(html.contains("Nous vous remercions"))
        assertFalse(html.contains("Votre agent prendra contact"))
        assertFalse(html.contains("recherche"))
    }

    @Test
    fun `assigned sale - the property is sold`() {
        val html = resolver.resolve(MandateAssignedMailet.TEMPLATE_LISTING, saleContext)

        assertTrue(html.contains("la vente de votre bien"))
        assertFalse(html.contains("la mise en location de votre bien"))
    }

    @Test
    fun `assigned to a search`() {
        val html = resolver.resolve(MandateAssignedMailet.TEMPLATE, context)

        assertTrue(html.contains("votre recherche immobilière"))
        assertTrue(html.contains("Je suis votre agent dédié"))
        assertTrue(html.contains("Je me présente"))
        assertTrue(html.contains("Je vous contacterai"))
        assertFalse(html.contains("Nous vous remercions"))
        assertFalse(html.contains("Votre agent prendra contact"))
    }

    @Test
    fun approved() {
        val html = resolver.resolve(MandateApprovedMailet.TEMPLATE_LISTING, context)

        assertTrue(html.contains("a signé le mandat de mise en location"))
        assertTrue(html.contains("Loyer</b>: 150 000 XAF / mois"))
        assertTrue(html.contains("commercialiser le bien"))
        assertFalse(html.contains("Budget"))
        assertTrue(html.contains("les candidats"))
        assertFalse(html.contains("les acheteurs"))
    }

    @Test
    fun `approved sale - a mandate to sell, visits with buyers`() {
        val html = resolver.resolve(MandateApprovedMailet.TEMPLATE_LISTING, saleContext)

        assertTrue(html.contains("a signé le mandat de vente"))
        assertTrue(html.contains("Prix</b>: 25 000 000 XAF"))
        assertTrue(html.contains("organiser les visites avec les acheteurs"))
        assertFalse(html.contains("mandat de mise en location"))
        assertFalse(html.contains("candidats"))
    }

    @Test
    fun `approved sale search - the client is buying`() {
        val html = resolver.resolve(
            MandateApprovedMailet.TEMPLATE,
            saleContext + mapOf("listing_type" to "Achat", "budget" to "25 000 000 XAF"),
        )

        assertTrue(html.contains("cherche à acheter un bien"))
        assertTrue(html.contains("Budget</b>: 25 000 000 XAF"))
        assertFalse(html.contains("recherche un bien"))
    }

    @Test
    fun `approved rental search - no purchase line`() {
        val html = resolver.resolve(MandateApprovedMailet.TEMPLATE, context + mapOf("budget" to "150 000 XAF / mois"))

        assertFalse(html.contains("cherche à acheter un bien"))
        assertTrue(html.contains("Budget</b>: 150 000 XAF / mois"))
    }
}
