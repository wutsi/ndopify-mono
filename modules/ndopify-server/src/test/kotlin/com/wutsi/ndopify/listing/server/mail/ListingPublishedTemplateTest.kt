package com.wutsi.ndopify.listing.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the templates, which [ListingPublishedMailetTest] doesn't (its resolver is a mock). */
class ListingPublishedTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Ray Sponsible",
        "customer_name" to "Ray Sponsible",
        "agent_name" to "Kim Possible",
        "title" to "Appartement à louer, 3 chambres, Yaounde (Bastos)",
        "city" to "Yaounde",
        "neighbourhood" to "Bastos",
        "listing_url" to "https://www.ndopify.com/yaounde/bastos/listing/1/appartement",
        "image_url" to "https://cdn.ndopify.com/7.jpg",
    )

    @Test
    fun customer() {
        val html = resolver.resolve(ListingPublishedMailet.TEMPLATE_CUSTOMER, context)

        assertTrue(html.contains("en ligne"))
        assertTrue(html.contains("Kim Possible"))
        assertTrue(html.contains("Appartement à louer, 3 chambres, Yaounde (Bastos)"))
        assertTrue(html.contains("Quartier</b>: Bastos"))
        assertTrue(html.contains("https://www.ndopify.com/yaounde/bastos/listing/1/appartement"))
        assertTrue(html.contains("https://cdn.ndopify.com/7.jpg"))
    }

    @Test
    fun agent() {
        val html = resolver.resolve(ListingPublishedMailet.TEMPLATE_AGENT, context)

        assertTrue(html.contains("publiée"))
        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("https://www.ndopify.com/yaounde/bastos/listing/1/appartement"))
        assertTrue(html.contains("https://cdn.ndopify.com/7.jpg"))
    }

    @Test
    fun `without cover photo, link, title nor neighbourhood`() {
        val html = resolver.resolve(
            ListingPublishedMailet.TEMPLATE_CUSTOMER,
            context - "image_url" - "listing_url" - "title" - "neighbourhood",
        )

        assertFalse(html.contains("cdn.ndopify.com"))
        assertFalse(html.contains("listing/1"))
        assertFalse(html.contains("Annonce</b>"))
        assertFalse(html.contains("Quartier"))
    }
}
