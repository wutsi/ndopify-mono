package com.wutsi.ndopify.visit.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the template, which [VisitFeedbackMailetTest] doesn't (its resolver is a mock). */
class VisitFeedbackTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Kim Possible",
        "customer_name" to "Ray Sponsible",
        "listing_title" to "Nice apartment",
        "visit_date" to "01/12/2026",
        "visit_time" to "10:00",
        "interest" to "Pas intéressé",
        "reason" to "trop cher",
        "comment" to "Trop cher pour moi",
        "visit_url" to "https://partner.ndopify.com/visits/1",
    )

    @Test
    fun render() {
        val html = resolver.resolve(VisitFeedbackMailet.TEMPLATE, context)

        assertTrue(html.contains("Kim Possible"))
        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("01/12/2026"))
        assertTrue(html.contains("10:00"))
        assertTrue(html.contains("Pas intéressé"))
        assertTrue(html.contains("trop cher"))
        assertTrue(html.contains("Trop cher pour moi"))
        assertTrue(html.contains("https://partner.ndopify.com/visits/1"))
        assertTrue(html.contains("Voir la visite"))
    }

    @Test
    fun `an interested answer without reason, comment nor link`() {
        val html = resolver.resolve(
            VisitFeedbackMailet.TEMPLATE,
            context - "reason" - "comment" - "visit_url" + ("interest" to "Intéressé"),
        )

        assertTrue(html.contains("Intéressé"))
        assertFalse(html.contains("Raison"))
        assertFalse(html.contains("Trop cher pour moi"))
        assertFalse(html.contains("https://partner.ndopify.com/visits/1"))
    }
}
