package com.wutsi.ndopify.match.server.mail

import com.github.mustachejava.DefaultMustacheFactory
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
        mustache = DefaultMustacheFactory(),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Ray Sponsible",
        "agent_full_name" to "Kim Possible",
        "listing_title" to "Nice apartment",
        "price_label" to "Loyer",
        "price" to "150 000 FCFA / mois",
        "listing_url" to "https://www.ndopify.com/match/1",
    )

    @Test
    fun `answered on the match's page`() {
        val html = resolver.resolve(MatchProposedMailet.TEMPLATE, context)

        assertTrue(html.contains("Kim Possible"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("150 000 FCFA / mois"))
        assertTrue(html.contains("https://www.ndopify.com/match/1"))
        assertTrue(html.contains("Voir le bien et répondre"))
        assertFalse(html.contains("Répondez à votre agent"))
    }

    @Test
    fun `without page - answered to the agent`() {
        val html = resolver.resolve(MatchProposedMailet.TEMPLATE, context - "listing_url")

        assertFalse(html.contains("Voir le bien et répondre"))
        assertTrue(html.contains("Répondez à votre agent"))
    }
}
