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
