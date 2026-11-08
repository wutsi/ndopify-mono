package com.wutsi.ndopify.match.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the template, which [MatchProposedMailetTest] doesn't (its resolver is a mock). */
class MatchProposedTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val listing = mapOf(
        "image_url" to "https://cdn.ndopify.com/7.jpg",
        "listing_title" to "Nice apartment",
        "price_label" to "Loyer",
        "price" to "150 000 FCFA / mois",
        "listing_url" to "https://www.ndopify.com/match/1",
    )
    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Ray Sponsible",
        "agent_full_name" to "Kim Possible",
        "agent_photo_url" to "https://cdn.ndopify.com/agent.jpg",
        "agent_whatsapp" to "+237 6 99 00 00 01",
        "agent_whatsapp_url" to "https://wa.me/237699000001",
        "online" to true,
        "listings" to listOf(listing),
    )

    @Test
    fun `answered on the match's page`() {
        val html = resolver.resolve(MatchProposedMailet.TEMPLATE, context)

        assertTrue(html.contains("Kim Possible"))
        assertTrue(html.contains("un bien qui correspond"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("150 000 FCFA / mois"))
        assertTrue(html.contains("https://www.ndopify.com/match/1"))
        assertTrue(html.contains("Voir le bien et répondre"))
        assertFalse(html.contains("Répondez à votre agent"))
        assertTrue(html.contains("src=\"https://cdn.ndopify.com/7.jpg\""))
        assertTrue(html.contains("<a href=\"https://www.ndopify.com/match/1\""))
        // The shared signature footer: the agent on one side, the tenant logo on the other
        assertTrue(html.contains("Votre agent"))
        assertTrue(html.contains("https://wa.me/237699000001"))
        assertTrue(html.contains("src=\"https://cdn.ndopify.com/agent.jpg\""))
    }

    @Test
    fun `no photo - no image`() {
        val html = resolver.resolve(MatchProposedMailet.TEMPLATE, context + mapOf("listings" to listOf(listing - "image_url")))

        assertTrue(html.contains("Nice apartment"))
        assertFalse(html.contains("cdn.ndopify.com/7.jpg"))
    }

    @Test
    fun `several listings - a link each`() {
        val other = listing + mapOf("listing_title" to "Big house", "listing_url" to "https://www.ndopify.com/match/2")

        val html = resolver.resolve(
            MatchProposedMailet.TEMPLATE,
            context + mapOf("count" to 2, "listings" to listOf(listing, other)),
        )

        assertTrue(html.contains("2 biens qui correspondent"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("Big house"))
        assertTrue(html.contains("https://www.ndopify.com/match/1"))
        assertTrue(html.contains("https://www.ndopify.com/match/2"))
        assertTrue(html.contains("ces biens vous intéressent"))
    }

    @Test
    fun `without page - answered to the agent`() {
        val html = resolver.resolve(
            MatchProposedMailet.TEMPLATE,
            context - "online" + mapOf("listings" to listOf(listing - "listing_url")),
        )

        assertFalse(html.contains("Voir le bien et répondre"))
        assertTrue(html.contains("Répondez à votre agent"))
        assertTrue(html.contains("src=\"https://cdn.ndopify.com/7.jpg\"")) // The photo, without a link
        assertFalse(html.contains("href=\"\""))
    }
}
