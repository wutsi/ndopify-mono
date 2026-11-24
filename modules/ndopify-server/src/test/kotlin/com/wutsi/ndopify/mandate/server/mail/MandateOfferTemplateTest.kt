package com.wutsi.ndopify.mandate.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the template, which [MandateOfferMailetTest] doesn't (its resolver is a mock). */
class MandateOfferTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
        "tenant_name" to "Ndopify",
        "recipient_name" to "Kim Possible",
        "listing_type" to "Achat",
        "property_type" to "Terrain",
        "city" to "Yaounde",
        "neighbourhoods" to "Bastos",
        "budget" to "10 000 000 XAF",
        "deadline_date" to "15/10/2026",
        "deadline_time" to "13:00",
        "offer_url" to "https://partner.ndopify.com",
    )

    @Test
    fun `a sale says its financing and the documents the buyer requires`() {
        val html = resolver.resolve(
            MandateOfferMailet.TEMPLATE,
            context + mapOf(
                "financing_type" to "Comptant",
                "title_documents" to "Titre foncier · Plan de bornage",
            ),
        )

        assertTrue(html.contains("Financement"))
        assertTrue(html.contains("Comptant"))
        assertTrue(html.contains("Documents exigés"))
        assertTrue(html.contains("Titre foncier · Plan de bornage"))
    }

    private val listingContext = mapOf(
        "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
        "tenant_name" to "Ndopify",
        "recipient_name" to "Kim Possible",
        "listing_type" to "Vente",
        "property_type" to "Terrain",
        "city" to "Yaounde",
        "neighbourhoods" to "Bastos",
        "deadline_date" to "15/10/2026",
        "deadline_time" to "13:00",
        "offer_url" to "https://partner.ndopify.com",
        "price_label" to "Prix",
        "price" to "45 000 000 XAF",
        "area" to 800,
        "available_from" to "01/12/2026",
    )

    @Test
    fun `a land sale lists its ownership documents`() {
        val html = resolver.resolve(
            MandateOfferMailet.TEMPLATE_LISTING,
            listingContext + mapOf("ownership_documents" to "Titre foncier · Plan de bornage"),
        )

        assertTrue(html.contains("Document de propriété"))
        assertTrue(html.contains("Titre foncier · Plan de bornage"))
        assertTrue(html.contains("45 000 000 XAF"))
        assertTrue(html.contains("01/12/2026"))
    }

    @Test
    fun `an offer without ownership documents has no document line`() {
        val html = resolver.resolve(
            MandateOfferMailet.TEMPLATE_LISTING,
            listingContext + mapOf("ownership_documents" to null),
        )

        assertFalse(html.contains("Document de propriété"))
        assertFalse(html.contains("Titre foncier"))
    }

    @Test
    fun `a building sale lists its étages and its unités`() {
        val html = resolver.resolve(
            MandateOfferMailet.TEMPLATE_LISTING,
            listingContext + mapOf(
                "property_type" to "Immeuble",
                "levels" to 3,
                "units" to 12,
            ),
        )

        assertTrue(html.contains("Étages"))
        assertTrue(html.contains("Unités"))
        assertTrue(html.contains("12"))
        assertFalse(html.contains("Équipements"))
        assertFalse(html.contains("Accès à l'eau"))
    }

    @Test
    fun `a sale search asks the agent to help a buyer`() {
        val html = resolver.resolve(MandateOfferMailet.TEMPLATE, context + mapOf("sale" to true))

        assertTrue(html.contains("Un client cherche à acheter un bien"))
        assertFalse(html.contains("Un client recherche un bien à"))
    }

    @Test
    fun `a rental search keeps its rental wording`() {
        val html = resolver.resolve(MandateOfferMailet.TEMPLATE, context + mapOf("sale" to false))

        assertTrue(html.contains("Un client recherche un bien à"))
        assertFalse(html.contains("cherche à acheter"))
    }

    @Test
    fun `a rental says no purchase answer`() {
        val html = resolver.resolve(
            MandateOfferMailet.TEMPLATE,
            context + mapOf("listing_type" to "Location", "financing_type" to null, "title_documents" to null),
        )

        assertFalse(html.contains("Financement"))
        assertFalse(html.contains("Documents exigés"))
    }
}
